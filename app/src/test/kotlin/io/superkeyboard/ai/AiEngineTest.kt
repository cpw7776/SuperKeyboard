package io.superkeyboard.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * JVM unit tests for [AiEngine] over a hand-written [FakeAiChatClient] (A2: fake the network seam).
 * The headline is the privacy egress guard (ADR D3): with the master flag OFF the engine must NOT
 * touch the client — asserted via [FakeAiChatClient.callCount] == 0, a fact the fake could not have
 * decided on its own (A4). Suspend `run` is exercised under [runTest] so assertions are awaited (A7).
 * Error/empty/boundary paths are covered (A3): unconfigured, HTTP error, network error, over-cap.
 */
class AiEngineTest {

    private val validConfig = AiConfig(
        enabled = true,
        endpointUrl = "https://api.example.com/v1",
        apiKey = "sk-test-key",
        model = "test-model",
        targetLanguage = "English"
    )

    // ---- Privacy egress guard (headline) ----

    @Test
    fun `disabled flag returns Disabled and never touches the client`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "hello world",
            config = validConfig.copy(enabled = false)
        )

        assertThat(result).isInstanceOf(EngineResult.Disabled::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    // ---- Unconfigured gates ----

    @Test
    fun `blank endpoint returns Unconfigured without touching the client`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "hello",
            config = validConfig.copy(endpointUrl = "")
        )

        assertThat(result).isInstanceOf(EngineResult.Unconfigured::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `blank api key returns Unconfigured`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "hello",
            config = validConfig.copy(apiKey = "  ")
        )

        assertThat(result).isInstanceOf(EngineResult.Unconfigured::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `blank model returns Unconfigured`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "hello",
            config = validConfig.copy(model = "")
        )

        assertThat(result).isInstanceOf(EngineResult.Unconfigured::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    // ---- Empty / boundary input ----

    @Test
    fun `blank input text returns Unconfigured without touching the client`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "   ",
            config = validConfig
        )

        assertThat(result).isInstanceOf(EngineResult.Unconfigured::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `over-cap input returns TooLong without touching the client`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake, maxInputChars = 10)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "0123456789X", // 11 chars, cap is 10
            config = validConfig
        )

        assertThat(result).isInstanceOf(EngineResult.TooLong::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `input exactly at the cap is allowed`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake, maxInputChars = 10)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "0123456789", // exactly 10
            config = validConfig
        )

        assertThat(result).isInstanceOf(EngineResult.Result::class.java)
        assertThat(fake.callCount).isEqualTo(1)
    }

    // ---- Happy path ----

    @Test
    fun `happy path returns the client text and sends a system plus user message pair`() = runTest {
        val fake = FakeAiChatClient(result = AiResult.Success("the rewritten text"))
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "make this better",
            config = validConfig
        )

        assertThat(result).isInstanceOf(EngineResult.Result::class.java)
        assertThat((result as EngineResult.Result).text).isEqualTo("the rewritten text")

        assertThat(fake.callCount).isEqualTo(1)
        assertThat(fake.lastModel).isEqualTo("test-model")

        val messages = fake.lastMessages!!
        assertThat(messages).hasSize(2)
        assertThat(messages[0].role).isEqualTo("system")
        assertThat(messages[1].role).isEqualTo("user")
        assertThat(messages[1].content).isEqualTo("make this better")
    }

    @Test
    fun `translate happy path threads the target language into the system prompt`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        engine.run(
            action = AiAction.TRANSLATE,
            inputText = "hello",
            config = validConfig.copy(targetLanguage = "German")
        )

        assertThat(fake.lastMessages!![0].content).contains("German")
    }

    @Test
    fun `preset happy path threads the preset prompt into the system message`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        engine.run(
            action = AiAction.PRESET,
            inputText = "hello",
            config = validConfig,
            presetPrompt = "Make it sound formal"
        )

        assertThat(fake.lastMessages!![0].content).contains("Make it sound formal")
    }

    // ---- Error mapping ----

    @Test
    fun `HttpError from the client maps to Error with a message`() = runTest {
        val fake = FakeAiChatClient(result = AiResult.HttpError(code = 401, message = "Unauthorized"))
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "hello",
            config = validConfig
        )

        assertThat(result).isInstanceOf(EngineResult.Error::class.java)
        assertThat((result as EngineResult.Error).message).isNotEmpty()
    }

    @Test
    fun `NetworkError from the client maps to Error with a message`() = runTest {
        val fake = FakeAiChatClient(result = AiResult.NetworkError(message = "timeout"))
        val engine = AiEngine(fake)

        val result = engine.run(
            action = AiAction.REWRITE,
            inputText = "hello",
            config = validConfig
        )

        assertThat(result).isInstanceOf(EngineResult.Error::class.java)
        assertThat((result as EngineResult.Error).message).isNotEmpty()
    }

    // ---- Test-connection probe (Phase 4 addition) ----

    @Test
    fun `probe with AI disabled returns Disabled and never touches the client`() = runTest {
        // Headline privacy assertion for the new probe path: the egress guard (ADR D3, invariant 1)
        // must hold for probe exactly as it does for run — AI off ⇒ no network work at all.
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.probe(validConfig.copy(enabled = false))

        assertThat(result).isInstanceOf(EngineResult.Disabled::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `probe with blank endpoint returns Unconfigured without touching the client`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.probe(validConfig.copy(endpointUrl = ""))

        assertThat(result).isInstanceOf(EngineResult.Unconfigured::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `probe with blank key returns Unconfigured without touching the client`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.probe(validConfig.copy(apiKey = "  "))

        assertThat(result).isInstanceOf(EngineResult.Unconfigured::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `probe with blank model returns Unconfigured without touching the client`() = runTest {
        val fake = FakeAiChatClient()
        val engine = AiEngine(fake)

        val result = engine.probe(validConfig.copy(model = ""))

        assertThat(result).isInstanceOf(EngineResult.Unconfigured::class.java)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun `probe configured sends a minimal request and maps Success to Result`() = runTest {
        val fake = FakeAiChatClient(result = AiResult.Success("OK"))
        val engine = AiEngine(fake)

        val result = engine.probe(validConfig)

        assertThat(result).isInstanceOf(EngineResult.Result::class.java)
        assertThat(fake.callCount).isEqualTo(1)
        assertThat(fake.lastModel).isEqualTo("test-model")
        // Minimal probe payload: a system instruction plus a user "ping".
        val messages = fake.lastMessages!!
        assertThat(messages).hasSize(2)
        assertThat(messages[0].role).isEqualTo("system")
        assertThat(messages[1].role).isEqualTo("user")
    }

    @Test
    fun `probe maps HttpError to a diagnosable Error`() = runTest {
        val fake = FakeAiChatClient(result = AiResult.HttpError(code = 401, message = "Invalid API key"))
        val engine = AiEngine(fake)

        val result = engine.probe(validConfig)

        assertThat(result).isInstanceOf(EngineResult.Error::class.java)
        assertThat((result as EngineResult.Error).message).contains("401")
    }
}
