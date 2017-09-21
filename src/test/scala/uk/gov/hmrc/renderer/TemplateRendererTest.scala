/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.renderer

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import play.api.i18n.{Lang, Messages}
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSCookie, WSResponse}
import play.api.i18n.MessagesApi
import play.twirl.api.Html
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.ws.{WSGet, WSHttpResponse}
import uk.gov.hmrc.play.http.{BadGatewayException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, _}
import scala.xml.{Elem, XML}


class TemplateRendererTest extends FlatSpec with Matchers with WithFakeApplication {

  implicit val m: Messages = new Messages(Lang("en"), fakeApplication.injector.instanceOf[MessagesApi])

  trait TemplateSetup {

    def httpCallSuccess: Boolean

    val bodyToReturn =
      """<html>
        |<head>
        |<title>
        |{{#pageTitle}}
        |{{ pageTitle }}
        |{{/pageTitle}}
        |{{^pageTitle}}
        |GOV.UK - The best place to find government services and information
        |{{/pageTitle}}
        |</title>
        |
        |{{{ head }}}
        |</head>
        |
        |<body class="{{{ bodyClasses }}}">
        |
        |<header role="banner" id="global-header" class="{{#nav}}with-proposition{{/nav}}">
        |    <div>
        |
        |        {{{ insideHeader }}}
        |
        |    </div>
        |</header>
        |
        |
        |{{{ afterHeader }}}
        |
        |{{{ article }}}
        |
        |<footer>
        |
        |    <div>
        |        {{{ footerTop }}}
        |        <div>
        |            {{{ footerLinks }}}
        |        </div>
        |    </div>
        |</footer>
        |
        |{{{ bodyEnd }}}
        |</body>
        |</html>""".stripMargin

    val templateRenderer = new TemplateRenderer {

      override val connection: WSGet = new WSGet {
        override val hooks: Seq[HttpHook] = Seq()
        override def doGet(url: String)(implicit  hc: HeaderCarrier): Future[HttpResponse] = {

          if(httpCallSuccess) {
            Future.successful(new WSHttpResponse(new WSResponse {
              override def cookie(name: String): Option[WSCookie] = ???
              override def underlying[T]: T = ???
              override def body: String = bodyToReturn
              override def bodyAsBytes: ByteString = ???
              override def cookies: Seq[WSCookie] = ???
              override def allHeaders: Map[String, Seq[String]] = ???
              override def xml: Elem = ???
              override def statusText: String = ???
              override def json: JsValue = ???
              override def header(key: String): Option[String] = ???
              override def status: Int = 200
            }))
          }
          else {
            Future.failed(new BadGatewayException("Bad Gateway"))
          }
        }

      }

      override def templateServiceBaseUrl: String = "http://template.service"
      override val refreshAfter: Duration = 10 minutes

    }

    def xhtmlFromString(htmlString: String): Elem = XML.loadString(htmlString)

    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  "TemplateRenderer" should "render template" in new TemplateSetup {
    val httpCallSuccess = true
    val expectedOutputHtml =
      """<html>
        |<head>
        |<title>
        |first
        |</title>
        |
        |head
        |</head>
        |
        |<body class="classes">
        |
        |<header role="banner" id="global-header" class="with-proposition">
        |    <div>
        |
        |        insideStory
        |
        |    </div>
        |</header>
        |
        |
        |<div>AfterParty</div>
        |
        |<p>Some Content</p>
        |
        |<footer>
        |
        |    <div>
        |        Top footer
        |        <div>
        |            Footer Links
        |        </div>
        |    </div>
        |</footer>
        |
        |End of body
        |</body>
        |</html>""".stripMargin

    val result = templateRenderer.renderDefaultTemplate(
      Html("<p>Some Content</p>"),
      Map(
        "pageTitle" -> "first",
        "head" -> Html("head"),
        "bodyClasses" -> "classes",
        "bodyEnd" -> Html("End of body"),
        "nav" -> true,
        "insideHeader" -> "insideStory",
        "afterHeader" ->  Html("<div>AfterParty</div>"),
        "footerTop" -> Html("Top footer"),
        "footerLinks" -> Some(Html("Footer Links"))
      )
    )

    result.toString() shouldBe expectedOutputHtml

  }

  it should "render a a template using template logic" in new TemplateSetup {
    val httpCallSuccess = true
    val expectedOutputHtml =
      """<html>
        |<head>
        |<title>
        |GOV.UK - The best place to find government services and information
        |</title>
        |
        |head
        |</head>
        |
        |<body class="classes">
        |
        |<header role="banner" id="global-header" class="with-proposition">
        |    <div>
        |
        |        insideStory
        |
        |    </div>
        |</header>
        |
        |
        |<div>AfterParty</div>
        |
        |<p>Some Content</p>
        |
        |<footer>
        |
        |    <div>
        |        Top footer
        |        <div>
        |            Footer Links
        |        </div>
        |    </div>
        |</footer>
        |
        |End of body
        |</body>
        |</html>""".stripMargin


    val result = templateRenderer.renderDefaultTemplate(
      Html("<p>Some Content</p>"),
      Map(
        "head" -> Html("head"),
        "bodyClasses" -> "classes",
        "bodyEnd" -> Html("End of body"),
        "nav" -> true,
        "insideHeader" -> Html("insideStory"),
        "afterHeader" ->  Html("<div>AfterParty</div>"),
        "footerTop" -> Html("Top footer"),
        "footerLinks" -> Some(Html("Footer Links"))
      )
    )

    result.toString() shouldBe expectedOutputHtml

  }


  it should "Fail if the http call is set to fail and there is no cached template" in new TemplateSetup {
    val httpCallSuccess = false

    override val bodyToReturn =
      """<html><body>{{{ article }}}</body></html>
      """.stripMargin

    an[Exception] shouldBe thrownBy {
      templateRenderer.renderDefaultTemplate(Html("<p>Some Content</p>"), Map.empty)
    }
  }


  it should "Not fail if the http call is set to fail and there is a cached template" in new TemplateSetup {
    var httpCallSuccess = true

    override val bodyToReturn =
      """<html><body>{{{ article }}}</body></html>
      """.stripMargin

    templateRenderer.renderDefaultTemplate(Html("<p>Some Content</p>"), Map.empty)

    httpCallSuccess = false

    templateRenderer.renderDefaultTemplate(Html("<p>Some Content</p>"), Map.empty)
  }

}


