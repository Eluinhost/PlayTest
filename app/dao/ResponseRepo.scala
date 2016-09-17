package dao

import javax.inject.Inject

import models.Response

class ResponseRepo @Inject()(val db : Database) {
  import db.api._

  val responses = db.responses

  def findBySlotIds(slotIds: Seq[Long]) = responses.filter(_.slotId inSet slotIds).result

  def insert(response: Response) = responses returning responses.map(_.id) into ((response, id) => response.copy(id = Some(id))) += response
}