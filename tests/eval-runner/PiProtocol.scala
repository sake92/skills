import ba.sake.tupson.*

case class PiConfig(
    model: String,
    reasoning: String
)

case class PiUsage(
    input: Long = 0,
    output: Long = 0,
    cacheRead: Long = 0,
    cacheWrite: Long = 0,
    reasoning: Long = 0,
    totalTokens: Long = 0
) derives JsonRW
case class PiContent(`type`: String = "", text: Option[String] = None) derives JsonRW
case class PiMessage(
    role: String = "",
    content: List[PiContent] = Nil,
    usage: Option[PiUsage] = None,
    stopReason: Option[String] = None
) derives JsonRW
case class PiArgs(
    path: Option[String] = None,
    command: Option[String] = None
) derives JsonRW
case class PiEvent(
    `type`: String,
    usage: Option[PiUsage] = None,
    message: Option[PiMessage] = None,
    toolName: Option[String] = None,
    args: Option[PiArgs] = None,
    isError: Option[Boolean] = None
) derives JsonRW
