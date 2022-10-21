package explore

import io.netty.handler.codec.http.websocketx.WebSocketChunkedInput
import zhttp.*
import zhttp.http.*
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.service.ChannelEvent
import zhttp.service.Server
import zhttp.socket.*
import zio.*
import zhttp.service.ChannelEvent.ChannelUnregistered

object ZHTTP extends ZIOAppDefault:

  // CSRF - Cross Site Request Forgery

  val app: UHttp[Request, Response] =
    Http.collect[Request] { //
      case Method.GET -> !! / "owls" => Response.text("Hello owls!")
    } @@ Middleware.csrfGenerate()

  val zApp: UHttpApp =
    Http.collectZIO[Request] { //
      case Method.POST -> !! / "owls" =>
        ZIO
          .succeed(scala.util.Random.alphanumeric.take(10).mkString)
          .map(s => Response.text(s"Hello, $s!"))
    } @@ Middleware.csrfValidate()

  val authApp: UHttpApp =
    Http.collect[Request] { //
      case Method.GET -> !! / "authenticated" / "owls" =>
        Response.text("Hello authenticated owls!")
    } @@ Middleware.basicAuthZIO(c => ZIO.succeed(c.uname == "g" && c.upassword == "pwd0"))
    // @@ Middleware.basicAuth("g", "pwd0")

  val combined = app ++ zApp ++ authApp

  // middleware
  // val wrapped = combined @@ Middleware.debug
  // val logged  = combined @@ Verbose.log

  // CORS - Cross Origin Resource Sharing
  val corsConfig = CorsConfig(
    anyOrigin = false,
    anyMethod = false,
    allowedOrigins = _.equals("localhost"),
    allowedMethods = Some(Set(Method.GET, Method.POST)),
  )

  val corsEnabledHttp = combined @@ Middleware.cors(corsConfig) @@ Verbose.log

  // websockets
  val sarcastic: String => String =
    _.toList.zipWithIndex
      .map { case (c, i) =>
        if i % 2 == 0 then c.toUpper else c.toLower
      }
      .mkString

  val wsLogic: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] { //
      case ChannelEvent(channel, ChannelEvent.ChannelRead(WebSocketFrame.Text(message))) =>
        channel.writeAndFlush(WebSocketFrame.text(sarcastic(message)))
      case ChannelEvent(_, ChannelEvent.UserEventTriggered(event))                       =>
        event match
          case ChannelEvent.UserEvent.HandshakeComplete =>
            ZIO.logInfo("Websocket started")
          case ChannelEvent.UserEvent.HandshakeTimeout  =>
            ZIO.logError("Websocket failed")
      case ChannelEvent(_, ChannelUnregistered)                                          =>
        ZIO.logInfo("Connection closed")
    }

  val wsApp = Http.collectZIO[Request] { //
    case Method.GET -> !! / "ws" => wsLogic.toSocketApp.toResponse
  }

  override def run =
    for
      _ <- Console.printLine("Started server at http://localhost:8080")
      _ <- Server.start(8080, wsApp)
    yield ()

object Verbose:
  def log[R, E >: Exception]: Middleware[R, E, Request, Response, Request, Response] =
    new:
      override def apply[R1 <: R, E1 >: E](
          http: Http[R1, E1, Request, Response]
      ): Http[R1, E1, Request, Response] =
        http
          .contramapZIO[R1, E1, Request] { req =>
            for
              _ <- Console.printLine(s"> ${req.method} ${req.path} ${req.version}")
              _ <- ZIO.foreach(req.headers.toList) { (name, value) =>
                     Console.printLine(s"> $name: $value")
                   }
            yield req
          }
          .mapZIO[R1, E1, Response] { res =>
            for
              _ <- Console.printLine(s"> ${res.status}")
              _ <- ZIO.foreach(res.headers.toList) { (name, value) =>
                     Console.printLine(s"> $name: $value")
                   }
            yield res
          }
