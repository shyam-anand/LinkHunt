package controllers

import java.net.URL

import de.l3s.boilerpipe.BoilerpipeProcessingException
import de.l3s.boilerpipe.document.TextBlock
import de.l3s.boilerpipe.extractors.ArticleExtractor
import de.l3s.boilerpipe.sax.BoilerpipeHTMLParser
import org.xml.sax.InputSource
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import collection.JavaConversions._

object ArticlePreviews extends Controller {
  def getpreview(urlIn: String) = Action { implicit request =>
    play.Logger.debug("[getpreview] received request for " + urlIn)

    val url = new URL(urlIn)

    try {
      val content = ArticleExtractor.INSTANCE.getText(url).replaceAll("\n", "<br/>")

      val parser = new BoilerpipeHTMLParser()
      parser.parse(new InputSource(url.openStream()))
      val title = parser.toTextDocument.getTitle

      //    val content = parser.toTextDocument.getContent
      //    play.Logger.debug("[getpreview] --- TextBlocks ---")
      //    val textBlocks: Seq[TextBlock] = parser.toTextDocument.getTextBlocks
      //    textBlocks foreach { textBlock =>
      //      play.Logger.debug("\tTag Level -- " + textBlock.getTagLevel.toString)
      //      val labelsOption = Option(textBlock.getLabels)
      //      labelsOption.foreach { labels =>
      //        if (labels.size > 0) {
      //          play.Logger.debug("\t --- Labels ---")
      //          labels.iterator.foreach { label =>
      //            play.Logger.debug(" --- " + label)
      //          }
      //        } else {
      //          play.Logger.debug(" --- no labels ---")
      //        }
      //      }
      //      play.Logger.debug(textBlock.getText)
      //    }

      play.Logger.debug("content from ArticleExtractor: " + content)
      val jsonResponse: JsValue = Json.obj(
        "status" -> true,
        "title" -> title,
        "content" -> content
      )

      Ok(jsonResponse)
    } catch {
      case e: BoilerpipeProcessingException =>
        play.Logger.error("[BoilerpipeProcessingException] " + e.getMessage)
        val jsonResponse: JsValue = Json.obj(
          "status" -> false,
          "title" -> "Unable to fetch article",
          "content" -> e.getMessage
        )
        Ok(jsonResponse)

      case e: Exception =>
        val jsonResponse: JsValue = Json.obj(
          "status" -> false,
          "title" -> "Unable to fetch article",
          "content" -> e.getMessage
        )
        Ok(jsonResponse)
    }
  }
}