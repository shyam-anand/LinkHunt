package models

import sorm.{Entity, Instance}

object SormDB extends Instance(entities = Seq(Entity[Person]()), url = "jdbc:h2:mem:test")
