package dao

import java.util.UUID
import javax.inject.Inject

import models.Poll

class PollRepo @Inject()(val db: Database) {
  import db.api._

  val polls = db.polls

  def findById(id: UUID) = polls.filter(_.id === id).result.headOption

  def insert(poll: Poll) = polls returning polls.map(_.id) into ((poll, id) => poll.copy(id = Some(id))) += poll

  def deleteById(id: UUID) = polls.filter(_.id === id).delete

  def isCorrectSecret(id: UUID, secret: UUID) = polls.filter(t => t.id === id && t.secret === secret).exists.result

  def exists(id: UUID) = polls.filter(_.id === id).exists.result
}