// Modified by SignalFx
import datadog.opentracing.mock.TestSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.jedis.JedisInstrumentation
import io.opentracing.tag.Tags
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import spock.lang.Shared

class JedisClientTest extends AgentTestRunner {

  public static final int PORT = 6399

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind 127.0.0.1")
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(PORT).build()
  @Shared
  Jedis jedis = new Jedis("localhost", PORT)

  def setupSpec() {
    println "Using redis: $redisServer.args"
    redisServer.start()
  }

  def cleanupSpec() {
    redisServer.stop()
//    jedis.close()  // not available in the early version we're using.
  }

  def setup() {
    jedis.flushAll()
    TEST_WRITER.start()
  }

  def "set command"() {
    jedis.set("foo", "bar")

    expect:
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    final TestSpan setTrace = trace.get(0)
    setTrace.getOperationName() == "redis.SET"
    setTrace.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    setTrace.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    setTrace.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    setTrace.getTags().get(Tags.DB_STATEMENT.getKey()) == "SET"
  }

  def "get command"() {
    jedis.set("foo", "bar")
    def value = jedis.get("foo")

    expect:
    value == "bar"
    TEST_WRITER.size() == 2
    def trace = TEST_WRITER.get(1)
    trace.size() == 1
    final TestSpan getSpan = trace.get(0)
    getSpan.getOperationName() == "redis.GET"
    getSpan.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    getSpan.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    getSpan.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    getSpan.getTags().get(Tags.DB_STATEMENT.getKey()) == "GET"
  }

  def "command with no arguments"() {
    jedis.set("foo", "bar")
    def value = jedis.randomKey()

    expect:
    value == "foo"
    TEST_WRITER.size() == 2
    def trace = TEST_WRITER.get(1)
    trace.size() == 1
    final TestSpan randomKeySpan = trace.get(0)
    randomKeySpan.getOperationName() == "redis.RANDOMKEY"
    randomKeySpan.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    randomKeySpan.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    randomKeySpan.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    randomKeySpan.getTags().get(Tags.DB_STATEMENT.getKey()) == "RANDOMKEY"
  }
}
