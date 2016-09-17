package dao

import java.util.UUID
import javax.inject.Inject

import models.Slot

class SlotRepo @Inject()(val db : Database) {
  import db.api._

  val slots = db.slots

  def findByPollId(pollId: UUID) = slots.filter(_.pollId === pollId).result

  def insert(slot: Slot) = slots returning slots.map(_.id) into ((slot, id) => slot.copy(id = Some(id))) += slot

  def exists(slot: Slot) = slots.filter(t => t.pollId === slot.pollId && t.day === slot.day && t.hour === slot.hour).exists.result

  def isAssignedToPoll(id: Long, pollId: UUID) = slots.filter(t => t.id === id && t.pollId === pollId).exists.result

  def deleteById(id: Long) = slots.filter(_.id === id).delete
}