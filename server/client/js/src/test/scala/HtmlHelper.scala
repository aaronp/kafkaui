
object HtmlHelper extends App {


  println(HtmlToScalaJs(    """
                              |
                              |<div class="container">
                              |    <h2>Modal Example</h2>
                              |    <button type="button" class="btn btn-primary" data-toggle="modal" data-target="#myModal">
                              |        Open modal
                              |    </button>
                              |</div>
                              |""".stripMargin))

  object HtmlToScalaJs {
    val quote = '"'
    val KeyValueR = s"(.*?) *=(.*)".r
    val PrefixedKeyValueR = s"([^=]+) +(.*?) *=(.*)".r
    val WhiteSpaceR = "( *)(.*?)( *)".r
    val EndR = "(.*?)</(.*)>".r
    val ElementOneLineR = "<(.*?) (.*?)>(.*)".r

    def apply(snippet : String) = {
      asScalaJsLines(snippet).map(_._2).mkString("\n")
    }

    def asScalaJsLines(snippet : String) = {
      def fmtPartial(partial : String) = {
        partial match {
          case EndR(text, _) => s"$quote$text$quote)"
          case other => other
        }
      }
      def fmtAttributes(atts : String, formatted : String): String = {
        def fmtKey(key : String) = {
          if (key.startsWith("aria") || key.startsWith("data-")) {
            s"attr($quote$key$quote)"
          } else {
            s"`$key`"
          }
        }
        atts match {
          case PrefixedKeyValueR(pfx, k, remaining) =>
            fmtAttributes(remaining, s"$formatted$pfx, ${fmtKey(k)} := ")
          case KeyValueR(k, remaining) =>
            val prefix = if (formatted.isEmpty) formatted else s", $formatted"
            fmtAttributes(remaining, s"$prefix${fmtKey(k)} := ")
          case other => s"$formatted$other"
        }
      }

      val converted = snippet.linesIterator.map {
        case WhiteSpaceR(indent, ElementOneLineR(name, atts, theRest), x) =>
          indent + s"$name(${fmtAttributes(atts.trim, "")})(${fmtPartial(theRest)}"
        case WhiteSpaceR(indent, EndR(before, _), _) => indent + ")"
        case WhiteSpaceR(indent, x, _) => indent + x
      }
      snippet.linesIterator.zip(converted)
    }

  }

}
