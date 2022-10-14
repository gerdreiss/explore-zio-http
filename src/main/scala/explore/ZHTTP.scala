package explore

import zio.*
import zhttp.*
import zhttp.http.*
import zhttp.service.Server

object ZHTTP extends ZIOAppDefault:

  val app: UHttp[Request, Response] = Http.collect[Request] {
    case Method.GET -> !! / "owls" => Response.text("Hello owls!")
  }

  val zApp: UHttpApp = Http.collectZIO[Request] {
    case Method.POST -> !! / "owls" => Random.nextString(10).map(s => Response.text(s"Hello, $s!"))
  }

  val combined = app ++ zApp

  // middleware
  val wrapped = combined @@ Middleware.debug
  val logged  = combined @@ Verbose.log

  val httpProgram: Task[Unit] =
    for
      _ <- Console.printLine("Started server at http://localhost:8080")
      _ <- Server.start(8080, logged)
    yield ()

  override def run = httpProgram

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
