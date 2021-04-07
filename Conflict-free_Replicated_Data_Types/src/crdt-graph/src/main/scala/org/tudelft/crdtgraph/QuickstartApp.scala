package org.tudelft.crdtgraph

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import DataStore._
import spray.json._

import scala.io.StdIn
import scala.concurrent.Future
import ClusterListener._
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import com.typesafe.config.ConfigFactory
import org.tudelft.crdtgraph.OperationLogs._

import scala.collection.mutable.{ArrayBuffer, HashMap}

//Custom case classes to parse vertices and arcs in post requests containing JSON
final case class VertexCaseClass(vertexName: String)

final case class ArcCaseClass(sourceVertex: String, targetVertex: String)

//JSON formats used to parse the body of post requests
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val vertexFormat = jsonFormat1(VertexCaseClass)
  implicit val arcFormat = jsonFormat2(ArcCaseClass)

  //OperationLog needs special format with custom write and read functions to parse it
  implicit object OperationLogFormat extends JsonFormat[OperationLog] {
    def write(obj: OperationLog): JsValue = {
      var jsonMap = HashMap[String, JsValue]()
      jsonMap("opType") = JsString(obj.opType).asInstanceOf[JsValue]
      jsonMap("operationUuid") = JsString(obj.operationUuid).asInstanceOf[JsValue]
      jsonMap("timestamp") = JsString(obj.timestamp).asInstanceOf[JsValue]
      if (obj.vertexName != "")
        jsonMap("vertexName") = JsString(obj.vertexName).asInstanceOf[JsValue]
      if (obj.vertexUuid != "")
        jsonMap("vertexUuid") = JsString(obj.vertexUuid).asInstanceOf[JsValue]
      if (obj.vertexUuids.nonEmpty)
        jsonMap("vertexUuids") = JsArray(obj.vertexUuids.toVector.map(x => JsString(x)).toVector).asInstanceOf[JsValue]
      if (obj.sourceVertex != "")
        jsonMap("sourceVertex") = JsString(obj.sourceVertex).asInstanceOf[JsValue]
      if (obj.targetVertex != "")
        jsonMap("targetVertex") = JsString(obj.targetVertex).asInstanceOf[JsValue]
      if (obj.arcUuid != "")
        jsonMap("arcUuid") = JsString(obj.arcUuid).asInstanceOf[JsValue]
      if (obj.arcUuids.nonEmpty)
        jsonMap("arcUuids") = JsArray(obj.arcUuids.map((id: String) => JsString(id)).toVector).asInstanceOf[JsValue]
      JsObject(jsonMap.toMap)
    }

    def read(json: JsValue): OperationLog = json match {
      case JsObject(fields) => {
        var log = new OperationLog()
        log.opType = fields("opType").convertTo[String]
        log.operationUuid = fields("operationUuid").convertTo[String]
        log.timestamp = fields("timestamp").convertTo[String]
        if (fields.contains("vertexName"))
          log.vertexName = fields("vertexName").convertTo[String]
        if (fields.contains("vertexUuid"))
          log.vertexUuid = fields("vertexUuid").convertTo[String]
        if (fields.contains("vertexUuids"))
          log.vertexUuids = fields("vertexUuids").convertTo[Array[String]]
        if (fields.contains("sourceVertex"))
          log.sourceVertex = fields("sourceVertex").convertTo[String]
        if (fields.contains("targetVertex"))
          log.targetVertex = fields("targetVertex").convertTo[String]
        if (fields.contains("arcUuid"))
          log.arcUuid = fields("arcUuid").convertTo[String]
        if (fields.contains("arcUuids"))
          log.arcUuids = fields("arcUuids").convertTo[Array[String]]
        log
      }
      case _ => deserializationError("Json is required")
    }
  }
}

object WebServer extends Directives with JsonSupport {
  var normalMessage = "Synchronizer not running"
  // needed to run the route
  implicit val system = ActorSystem("crdt-graph")
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  //True is returned with success, false is returned with failure
  val trueString = "true"
  val falseString = "false"

  // Configloader
  lazy val config = ConfigFactory.load()
  var kubernetesActive = config.getBoolean("my-app.kubernetesActive")


  def main(args: Array[String]) {
    if (kubernetesActive) {
        ClusterListener.startManager(system)
    }

    Synchronizer.synchronize(kubernetesActive, system, materializer)

    val route: Route = {
      //Route to add a vertex to the datastore. Returns true on success, false otherwise
      //HTTP Post request in the following form: {"vertexName": "abc"}
      //Only one vertex per request
      post {
        pathPrefix("addvertex") {
          entity(as[VertexCaseClass]) { vertex =>
            if (DataStore.addVertex(vertex.vertexName)) {
              complete(trueString)
            } else {
              complete(StatusCodes.BadRequest, falseString)
            }
          }
        }
      } ~
        //Route to add a arc between to vertices to the datastore. Returns true on success, false otherwise
        //Source vertex that the new arc connects to need to exist beforehand, otherwise false is returned
        //HTTP Post request in the following form: {"sourceVertex":"xyz", "targetVertex":"abc"}
        //Only one arc per request
        post {
          pathPrefix("addarc") {
            entity(as[ArcCaseClass]) { arc =>
              val src = arc.sourceVertex
              val dst = arc.targetVertex

              if (DataStore.addArc(src, dst)) {
                complete(trueString)
              } else {
                complete(StatusCodes.BadRequest, falseString)
              }
            }
          }
        } ~
        //Route to remove a vertex from the datastore. Returns true on success, false otherwise
        //Vertex needs to exist in the datastore beforehand, otherwise false is returned
        //HTTP Delete request in the following form: {"vertexName": "abc"}
        //Only one vertex per request
        delete {
          pathPrefix("removevertex") {
            entity(as[VertexCaseClass]) { vertex =>
              val vertexName = vertex.vertexName
              if (DataStore.removeVertex(vertexName)) {
                complete(trueString)
              } else {
                complete(StatusCodes.BadRequest, falseString)
              }
            }
          }
        } ~
        //Route to remove an arc between two vertices in the datastore. Returns true on success, false otherwise
        //Arc and the source vertex need to exist beforehand, otherwise false is returned
        //HTTP Delete request in the following form: {"sourceVertex":"xyz", "targetVertex":"abc"}
        //Only one arc per request
        delete {
          pathPrefix("removearc") {
            entity(as[ArcCaseClass]) { arc =>
              val src = arc.sourceVertex
              val dst = arc.targetVertex
              if (DataStore.removeArc(src, dst)) {
                complete(trueString)
              } else {
                complete(StatusCodes.BadRequest, falseString)
              }
            }
          }
        } ~
        //Route to synchronize changes between instances of the system.
        //HTTP Post request with a JSON body with a serialized collection of OperationLog.
        post {
          pathPrefix("applychanges") {
            entity(as[JsValue]) { oplog =>
              var logs = oplog.convertTo[Vector[OperationLog]]
              if (DataStore.applyChanges(logs)) {
                complete(trueString)
              }
              else {
                complete(falseString)
              }
            }
          }
        } ~
        //Route to lookup if a vertex exists in the datastore. Returns true on success, false otherwise
        //HTTP Get request in the following form (e.g. with cURL): "curl http://localhost:8081/lookupvertex\?vertexName\=abc"
        //Only one vertex per request
        get {
          pathPrefix("lookupvertex") {
            parameter("vertexName") { vertexName =>
              if (DataStore.lookUpVertex(vertexName)) {
                complete(trueString)
              } else {
                complete(falseString)
              }
            }
          }
        } ~
        //Route to lookup if an arc exists in the datastore. Returns true on success, false otherwise
        //HTTP Get request in the following form (e.g. with cURL): "curl http://localhost:8080/lookuparc\?sourceVertex\=abc\&targetVertex\=xyz"
        //Only one arc per request
        get {
          pathPrefix("lookuparc") {
            parameter("sourceVertex", "targetVertex") { (sourceVertex, targetVertex) =>
              if (DataStore.lookUpArc(sourceVertex, targetVertex)) {
                complete(trueString)
              } else {
                complete(falseString)
              }
            }
          }
        } ~
        //Route for debug purposes
        get {
          pathPrefix("debug-get-changes") {
            var changes = DataStore.getLastChanges(0).toVector
            complete(changes.map(log => log.toJson))
          }
        } ~
          get {
            pathPrefix("address") {
              if (kubernetesActive) {
                var message = "This is my address! \n"
                message += ClusterListener.getSelfAddress(system)
                message += "\nAnd these are the addresses I am broadcasting to: \n"
                message += ClusterListener.getBroadcastAddresses(system) + "\n"

                complete(message)
              } else {
                complete(StatusCodes.BadRequest, falseString)
              }
            }
          }

    }

    val port = if (args.length > 0) args(0).toInt else 8080
    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", port)
    println(s"Server online at http://localhost:" + port)


  }
}