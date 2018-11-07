// Modified by SignalFx
import datadog.trace.agent.test.utils.TestSpan
import datadog.trace.agent.test.AgentTestRunner

class AkkaActorTest extends AgentTestRunner {

  def "akka #testMethod"() {
    setup:
    AkkaActors akkaTester = new AkkaActors()
    akkaTester."$testMethod"()

    TEST_WRITER.waitForTraces(1)
    List<TestSpan> trace = TEST_WRITER.get(0)

    expect:
    TEST_WRITER.size() == 1
    trace.size() == 2
    trace[0].getOperationName() == "AkkaActors.$testMethod"
    findSpan(trace, "$expectedGreeting, Akka").parentId == trace[0].spanId

    where:
    testMethod     | expectedGreeting
    "basicTell"    | "Howdy"
    "basicAsk"     | "Howdy"
    "basicForward" | "Hello"
  }

  private TestSpan findSpan(List<TestSpan> trace, String opName) {
    for (TestSpan span : trace) {
      if (span.getOperationName() == opName) {
        return span
      }
    }
    return null
  }
}
