package io.superkeyboard.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM unit tests for [ResponseMapper.mapHttpResponse] — the pure response→[AiResult] mapping
 * extracted from [OkHttpAiChatClient] so the diagnosability paths are testable without a live server
 * (anti-pattern A2: no need to stand up a socket). The headline is the Phase-4 bug: a misconfigured
 * endpoint that returns a 200 HTML page was previously mapped to a bare opaque "Unexpected response
 * from endpoint" with NO code and NO snippet, making it undiagnosable. Now the mapping MUST carry the
 * HTTP code and a bounded body snippet (I1: surface errors, don't swallow) — but NEVER the request
 * body or the API key (privacy invariant 3): only the server's own response body is reflected back.
 */
class ResponseMappingTest {

    private val validJson = """
        {"choices":[{"message":{"role":"assistant","content":"the rewritten text"}}]}
    """.trimIndent()

    @Test
    fun `200 with valid JSON and non-blank content maps to Success`() {
        val result = ResponseMapper.mapHttpResponse(isSuccessful = true, code = 200, body = validJson)

        assertThat(result).isInstanceOf(AiResult.Success::class.java)
        assertThat((result as AiResult.Success).text).isEqualTo("the rewritten text")
    }

    @Test
    fun `200 with HTML body maps to NetworkError whose message contains the code and a snippet`() {
        val html = "<!DOCTYPE html><html><head><title>Login</title></head><body>Sign in</body></html>"

        val result = ResponseMapper.mapHttpResponse(isSuccessful = true, code = 200, body = html)

        assertThat(result).isInstanceOf(AiResult.NetworkError::class.java)
        val msg = (result as AiResult.NetworkError).message
        assertThat(msg).contains("200")
        // A snippet of the server's own body is reflected so the user can see "it's HTML / wrong URL".
        assertThat(msg).contains("<!DOCTYPE html>")
    }

    @Test
    fun `200 with garbage body maps to NetworkError containing the code`() {
        val result = ResponseMapper.mapHttpResponse(isSuccessful = true, code = 200, body = "not json at all")

        assertThat(result).isInstanceOf(AiResult.NetworkError::class.java)
        val msg = (result as AiResult.NetworkError).message
        assertThat(msg).contains("200")
        assertThat(msg).contains("not json at all")
    }

    @Test
    fun `200 body snippet is bounded so a huge HTML page is not echoed in full`() {
        val huge = "<html>" + "x".repeat(5000) + "</html>"

        val result = ResponseMapper.mapHttpResponse(isSuccessful = true, code = 200, body = huge)

        assertThat(result).isInstanceOf(AiResult.NetworkError::class.java)
        // Far shorter than the 5000+ char body: the snippet is capped, plus the "Unexpected..." prefix.
        assertThat((result as AiResult.NetworkError).message.length).isLessThan(400)
    }

    @Test
    fun `200 valid JSON but empty content maps to an error that includes the code`() {
        val emptyContent = """{"choices":[{"message":{"role":"assistant","content":"  "}}]}"""

        val result = ResponseMapper.mapHttpResponse(isSuccessful = true, code = 200, body = emptyContent)

        // Either HttpError(code, ...) — the message/code must carry the 200 so it's diagnosable.
        assertThat(result).isInstanceOf(AiResult.HttpError::class.java)
        assertThat((result as AiResult.HttpError).code).isEqualTo(200)
    }

    @Test
    fun `200 valid JSON with no choices maps to an error that includes the code`() {
        val noChoices = """{"choices":[]}"""

        val result = ResponseMapper.mapHttpResponse(isSuccessful = true, code = 200, body = noChoices)

        assertThat(result).isInstanceOf(AiResult.HttpError::class.java)
        assertThat((result as AiResult.HttpError).code).isEqualTo(200)
    }

    @Test
    fun `401 with an error body maps to HttpError carrying the code and a body snippet`() {
        val body = """{"error":{"message":"Invalid API key"}}"""

        val result = ResponseMapper.mapHttpResponse(isSuccessful = false, code = 401, body = body)

        assertThat(result).isInstanceOf(AiResult.HttpError::class.java)
        val err = result as AiResult.HttpError
        assertThat(err.code).isEqualTo(401)
        assertThat(err.message).contains("Invalid API key")
    }

    @Test
    fun `500 with an error body maps to HttpError carrying the code`() {
        val result = ResponseMapper.mapHttpResponse(isSuccessful = false, code = 500, body = "Internal Server Error")

        assertThat(result).isInstanceOf(AiResult.HttpError::class.java)
        val err = result as AiResult.HttpError
        assertThat(err.code).isEqualTo(500)
        assertThat(err.message).contains("Internal Server Error")
    }

    @Test
    fun `non-2xx error body snippet is bounded`() {
        val huge = "E".repeat(5000)

        val result = ResponseMapper.mapHttpResponse(isSuccessful = false, code = 500, body = huge)

        assertThat(result).isInstanceOf(AiResult.HttpError::class.java)
        assertThat((result as AiResult.HttpError).message.length).isLessThan(400)
    }
}
