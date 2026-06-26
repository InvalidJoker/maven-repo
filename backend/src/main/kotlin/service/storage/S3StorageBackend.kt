package de.joker.service.storage

import de.joker.config.StorageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File
import java.io.InputStream
import java.net.URI

class S3StorageBackend(config: StorageConfig.S3) : StorageBackend {

    private val bucket = config.bucket

    private val client: S3Client = S3Client.builder()
        .region(Region.of(config.region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKeyId, config.secretAccessKey),
            ),
        )
        .httpClient(UrlConnectionHttpClient.create())
        .apply {
            config.endpoint?.let { endpoint ->
                endpointOverride(URI.create(endpoint))
                // S3-compatible stores generally require path-style addressing.
                serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            }
        }
        .build()

    override suspend fun list(repository: String, path: String): List<StorageEntry>? = withContext(Dispatchers.IO) {
        val segments = cleanSegments(path)
        val prefix = (listOf(repository) + segments).joinToString("/") + "/"

        val response = client.listObjectsV2(
            ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).delimiter("/").build(),
        )

        val entries = ArrayList<StorageEntry>()
        for (commonPrefix in response.commonPrefixes()) {
            val name = commonPrefix.prefix().removePrefix(prefix).trimEnd('/')
            if (name.isNotEmpty()) entries += StorageEntry(name, directory = true, size = null)
        }
        for (obj in response.contents()) {
            val name = obj.key().removePrefix(prefix)
            // Skip the directory placeholder and anything nested deeper.
            if (name.isEmpty() || name.contains('/')) continue
            entries += StorageEntry(name, directory = false, size = obj.size())
        }

        // A non-root prefix with no children does not exist as a directory.
        if (entries.isEmpty() && segments.isNotEmpty()) null else entries
    }

    override suspend fun read(repository: String, path: String): StorageObject? = withContext(Dispatchers.IO) {
        val request = GetObjectRequest.builder().bucket(bucket).key(objectKey(repository, path)).build()
        try {
            val stream = client.getObject(request)
            StorageObject(stream.response().contentLength(), stream)
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }
    }

    override suspend fun exists(repository: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val request = HeadObjectRequest.builder().bucket(bucket).key(objectKey(repository, path)).build()
        try {
            client.headObject(request)
            true
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) false else throw e
        }
    }

    override suspend fun write(repository: String, path: String, input: InputStream): Boolean =
        withContext(Dispatchers.IO) {
            val segments = cleanSegments(path)
            if (segments.isEmpty()) return@withContext false

            // S3 putObject needs a known content length, so buffer through a temp file.
            val temp = File.createTempFile("maven-upload", ".tmp")
            try {
                temp.outputStream().use { output -> input.copyTo(output) }
                client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(objectKey(repository, path)).build(),
                    RequestBody.fromFile(temp),
                )
                true
            } finally {
                temp.delete()
            }
        }

    private fun cleanSegments(path: String): List<String> =
        path.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }

    private fun objectKey(repository: String, path: String): String =
        (listOf(repository) + cleanSegments(path)).joinToString("/")
}