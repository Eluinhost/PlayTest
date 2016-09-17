package controllers

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

import dao.{Database, PollRepo}
import exceptions.IncorrectSecretException
import models.Poll
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class PollsController @Inject()(val pollRepo: PollRepo, val db: Database)(implicit exec: ExecutionContext) extends Controller {
  import db.api._

  // Formatting for reading poll from request JSON
  // Name must be 3 <= length <= 128
  // If open time is not provided it opens now
  // closes time must be provided
  // Secret is auto-generated
  implicit val pollReads : Reads[Poll] = (
    (JsPath \ "name").read[String] (minLength[String](3) <~ maxLength[String](128)) and
    (JsPath \ "opens").readNullable[OffsetDateTime] and
    (JsPath \ "closes").read[OffsetDateTime]
  )((name, opens, closes) => Poll(
    name = name,
    opens = opens.getOrElse(OffsetDateTime.now()),
    closes = closes,
    secret = UUID.randomUUID()
  ))

  // Formatting for displaying poll details
  // Does not show secret by default
  implicit val pollWrites = new Writes[Poll] {
    def writes(poll: Poll) = Json.obj(
      "id" -> poll.id,
      "name" -> poll.name,
      "opens" -> poll.opens,
      "closes" -> poll.closes
    )
  }

  // Same as pollWrites but adds the secret too, for initial creation
  val pollWritesWithSecret = new Writes[Poll] {
    override def writes(poll: Poll) = Json.toJson(poll).asInstanceOf[JsObject] + ("secret" -> JsString(poll.secret.toString))
  }

  // Show a specific poll
  def get(id: UUID) = Action async {
    db run pollRepo.findById(id) map {
      case Some(poll) => Ok(Json.toJson(poll))
      case None => NotFound
    }
  }

  // Create a new poll
  def post = Action.async(BodyParsers.parse.json) {
    _.body.validate[Poll].fold(
      errors => Future successful BadRequest(Json.obj("error" -> JsError.toJson(errors))),
      poll => db run pollRepo.insert(poll) map { poll => poll.id match { // ID only exists for valid db objects
        case Some(_) => Created(Json.toJson(poll)(pollWritesWithSecret))
        case None =>
          Logger.error("Failed to create a new poll")
          InternalServerError
      }}
    )
  }

  // Deletes an existing poll using it's secret
  def delete(id: UUID, secret: UUID) = Action.async {
    db run (for {
      correct <- pollRepo.isCorrectSecret(id, secret)
      deleted <- if (correct) pollRepo.deleteById(id) else DBIO.failed(IncorrectSecretException())
    } yield deleted).transactionally.asTry map {
      case Success(_) => NoContent
      case Failure(IncorrectSecretException()) => Unauthorized
      case Failure(ex) =>
        Logger.error("Failed to delete a poll", ex)
        InternalServerError
    }
  }
}
