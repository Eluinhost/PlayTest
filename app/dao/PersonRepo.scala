package dao

import java.util.UUID
import javax.inject.Inject

import models.Person

class PersonRepo @Inject() (val db : Database) {
  import db.api._

  val people = db.people

  def findByPollId(pollId: UUID) = people.filter(_.pollId === pollId).result

  def insert(person: Person) = people returning people.map(_.id) into ((person, id) => person.copy(id = Some(id))) += person

  def exists(pollId: UUID, name: String) = people.filter(t => t.pollId === pollId && t.name === name).exists.result

  def deleteById(pollId: UUID, id: Long) = people.filter(t => t.pollId === pollId && t.id === id).delete
}