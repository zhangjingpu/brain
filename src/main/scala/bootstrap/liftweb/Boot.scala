/**
 * Copyright 2013 Israel Freitas (israel.araujo.freitas@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bootstrap.liftweb

import java.util.Locale
import scala.collection.JavaConverters.mapAsJavaMapConverter
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx
import brain.config.Config
import brain.db.GraphDb
import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.http.Html5Properties
import net.liftweb.http.LiftRules
import net.liftweb.http.LiftRulesMocker.toLiftRules
import net.liftweb.http.Req
import net.liftweb.http.SessionVar
import net.liftweb.http.provider.HTTPRequest
import net.liftweb.util.Vendor.valToVendor
import com.orientechnologies.orient.core.metadata.schema.OType
import brain.models.Knowledge
import brain.models.Configuration
import brain.rest.BrainRest
import brain.db.OrientDbServer

// Inspirado em: http://stackoverflow.com/questions/8305586/where-should-my-sessionvar-object-be
object appSession extends SessionVar[Map[String, Any]](Map()) {
    val LocaleKey = "locale"
    val UserKey = "user"
}

class Boot {

    def boot = {

        // Where find snippet and comet
        LiftRules.addToPackages("brain")
        
        LiftRules.dispatch.append(BrainRest)

        // Full support to Html5
        LiftRules.htmlProperties.default.set((r: Req) => new Html5Properties(r.userAgent))

        // i18N
        LiftRules.localeCalculator = localeCalculator _
        LiftRules.resourceNames = "i18n/messages" :: LiftRules.resourceNames
        LiftRules.resourceNames = "props" :: LiftRules.resourceNames

        createDbUnlessAlreadyExists
        
    }

    def createDbUnlessAlreadyExists = {
        println("DB URI: "+Config.getGraphDbUri)
        val orientServerAdmin = new OServerAdmin("remote:localhost")
        orientServerAdmin.connect(Config.getGraphDbUser, Config.getGraphDbPassword)
        try {
        	println("before to creating the schema")
        	println(orientServerAdmin.listDatabases().keySet())
            if(!orientServerAdmin.listDatabases().keySet().contains(Config.getGraphDbName)){
                println("creating the database...")
            	orientServerAdmin.createDatabase(Config.getGraphDbName, "graph", "plocal")
            	//createSchema
                createRootVertexAndConf
            }
        }
        catch{
            case t :Throwable=> {
                println(t.getStackTraceString)
                orientServerAdmin.dropDatabase("plocal")
                throw new Exception("Was not possible to create the database. Cause: " + t.getCause())
            }
        }
        finally {
            if(orientServerAdmin.isConnected()) orientServerAdmin.close(false)
        }
    }
    
    def createRootVertexAndConf{
    	println("creating the root...")
        implicit val db = GraphDb.get
        try {
            val knowledge = Knowledge("Root").save
            db.commit // https://github.com/orientechnologies/orientdb/wiki/Transactions#optimistic-transaction
            Configuration(knowledge.getId().toString(), 3).save
            db.commit // https://github.com/orientechnologies/orientdb/wiki/Transactions#optimistic-transaction
            println("root created.")
        }
        catch {
        	
		  case t : Throwable => db.rollback; println(t.getMessage()); throw new Exception("Was not possible to create the root node. Cause: " + t.getStackTrace())
		}
        finally{
            if(db != null && !db.isClosed()) db.shutdown()
        }
//        val db = GraphDb.get
//        try{
//        	val root = db.addVertex("class:Knowledge", Map[String, Object]("name"->"Root").asJava)
//        	db.commit // https://github.com/orientechnologies/orientdb/wiki/Transactions#optimistic-transaction
//        	db.addVertex("class:Conf", Map[String, Object]("rootId"->root.getId().toString(), "defaultDepthTraverse"->new Integer(3)).asJava)
//   			db.commit
//        }
//        catch {
//		  case t : Throwable => db.rollback; throw new Exception("Was not possible to create the root node. Cause: " + t.getCause())
//		}
//        finally {
//        	if(db != null && !db.isClosed()) db.shutdown()
//        }
        
        
    }
    
    def createSchema(){
        val db:OrientGraphNoTx = GraphDb.getNoTx
        try {
        	db.createEdgeType("Include")
        	db.createEdgeType("Division")
        	db.createVertexType("Conf")
        	db.createVertexType("Knowledge").createProperty("name", OType.STRING).setMandatory(true).setMin("2").setMax("40")
        	db.createVertexType("Topic").createProperty("name", OType.STRING).setMandatory(true).setMin("2").setMax("40")
        	val teachingVertex = db.createVertexType("Teaching")
        	teachingVertex.createProperty("whenTheUserSays", OType.STRING).setMandatory(true).setMin("1").setMax("100")
        	teachingVertex.createProperty("respondingTo", OType.STRING).setMandatory(true).setMin("1").setMax("100")
        	teachingVertex.createProperty("memorize", OType.STRING).setMandatory(true).setMin("3").setMax("60")
        	teachingVertex.createProperty("say", OType.STRING).setMandatory(true).setMin("1").setMax("500")
        }
        catch {
		  case t : Throwable => {
		       db.drop
		       throw new Exception("Was not possible to create the databse schema. Cause: " + t.getCause())
		  }
		}
        finally {
        	if(db != null && !db.isClosed()) db.shutdown()
        }
    }

    def localeCalculator(request: Box[HTTPRequest]): Locale = {
        def calcLocale: Locale = {
            val locale = LiftRules.defaultLocaleCalculator(request)
            appSession.set(Map(appSession.LocaleKey -> locale))
            locale
        }

        appSession.is.get(appSession.LocaleKey) match {
            case Some(l: Locale) => l
            case _               => calcLocale
        }
    }
}