package com.github.dvarelap.stilt

import com.github.dvarelap.stilt.jackson.DefaultJacksonJsonSerializer
import com.twitter.app.App
import com.twitter.server.Stats
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._

class Controller extends App with Stats {

  val routes                     = new RouteVector[(HttpMethod, String, PathPattern, Request => Future[ResponseBuilder])]
  val stats                      = statsReceiver.scope("Controller")
  var serializer: JsonSerializer = DefaultJacksonJsonSerializer

  var notFoundHandler: Option[(Request) => Future[ResponseBuilder]] = None
  var errorHandler   : Option[(Request) => Future[ResponseBuilder]] = None

  def get(path: String)   (callback: Request => Future[ResponseBuilder]) { addRoute(HttpMethod.GET,    path)(callback) }
  def delete(path: String)(callback: Request => Future[ResponseBuilder]) { addRoute(HttpMethod.DELETE, path)(callback) }
  def post(path: String)  (callback: Request => Future[ResponseBuilder]) { addRoute(HttpMethod.POST,   path)(callback) }
  def put(path: String)   (callback: Request => Future[ResponseBuilder]) { addRoute(HttpMethod.PUT,    path)(callback) }
  def head(path: String)  (callback: Request => Future[ResponseBuilder]) { addRoute(HttpMethod.HEAD,   path)(callback) }
  def patch(path: String) (callback: Request => Future[ResponseBuilder]) { addRoute(HttpMethod.PATCH,  path)(callback) }
  def options(path: String)(callback: Request => Future[ResponseBuilder]){ addRoute(HttpMethod.OPTIONS, path)(callback) }

  def notFound(callback: Request => Future[ResponseBuilder]) { notFoundHandler = Option(callback) }
  def error(callback: Request => Future[ResponseBuilder]) { errorHandler = Option(callback) }

  def render: ResponseBuilder = new ResponseBuilder(serializer)
  def route: Router = new Router(this)

  def redirect(location: String, message: String = "", permanent: Boolean = false): ResponseBuilder = {
    val msg = if (message == "") "Redirecting to <a href=\"%s\">%s</a>.".format(location, location)
    else message

    val code = if (permanent) 301 else 302

    render.plain(msg).status(code).header("Location", location)
  }

  def respondTo(r: Request)(callback: PartialFunction[ContentType, Future[ResponseBuilder]]): Future[ResponseBuilder] = {
    if (r.routeParams.get("format").isDefined) {
      val format      = r.routeParams("format")
      val mime        = FileService.getContentType("." + format)
      val contentType = ContentType(mime).getOrElse(new ContentType.All)

      if (callback.isDefinedAt(contentType)) {
        callback(contentType)
      } else {
        throw new UnsupportedMediaType
      }
    } else {
      r.accepts.find { mimeType =>
        callback.isDefinedAt(mimeType)
      } match {
        case Some(contentType) =>
          callback(contentType)
        case None =>
          throw new UnsupportedMediaType
      }
    }
  }

  def addRoute(method: HttpMethod, path: String)(callback: Request => Future[ResponseBuilder]) {
    val regex = SinatraPathPatternParser(path)
    routes.add((method, path, regex, (r) => {
      stats.timeFuture("%s/Root/%s".format(method.toString, path.stripPrefix("/"))) {
        callback(r)
      }
    }))
  }
}
