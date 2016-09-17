package util

import com.github.tminglei.slickpg._
import slick.driver.JdbcProfile

trait CustomPostgresDriver extends  ExPostgresDriver
                              with  PgArraySupport
                              with  PgDate2Support
//                              with  PgPlayJsonSupport TODO
                              with  PgNetSupport
                              with  PgLTreeSupport
                              with  PgRangeSupport
                              with  PgHStoreSupport
                              with  PgSearchSupport {
//  override val pgjson = "jsonb" TODO

  override protected def computeCapabilities = super.computeCapabilities + JdbcProfile.capabilities.insertOrUpdate

  override val api = new API  with ArrayImplicits
                              with DateTimeImplicits
//                              with PlayJsonImplicits TODO
                              with NetImplicits
                              with LTreeImplicits
                              with RangeImplicits
                              with HStoreImplicits
                              with SearchImplicits
                              with SearchAssistants {}
}

object CustomPostgresDriver extends CustomPostgresDriver