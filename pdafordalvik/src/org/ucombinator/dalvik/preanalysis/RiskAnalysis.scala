package org.ucombinator.dalvik.preanalysis
import org.ucombinator.playhelpers.AnalysisHelperThread
import org.ucombinator.dalvik.syntax.MethodDef
import org.ucombinator.utils.AIOptions
import org.ucombinator.utils.StringUtils
import scala.tools.nsc.io.Directory
import java.io.File
import java.io.FileWriter
import org.ucombinator.dalvik.syntax.Stmt
import models.JsonOutputForMethod
import org.ucombinator.dalvik.syntax.LineStmt
import org.ucombinator.utils.CommonUtils
import models.SourceLocationInfo
import spray.json._
import DefaultJsonProtocol._

import org.ucombinator.dalvik.syntax._



object RiskAnalysis {
   def Desc[T : Ordering] = implicitly[Ordering[T]].reverse
 
  // has to be called right before expensive analysis
   def computeAndSetOverallRisk  {
       
       val clsTbl = Thread.currentThread().asInstanceOf[AnalysisHelperThread].classTable
       //println("classTable?????")
       //clsTbl.foreach(println)
       clsTbl.foreach {
         case (k, clsDef) => {
           clsDef.riskRank = clsDef.computeClassRisk 
           val meths = clsDef.methods
           meths.foreach(md => {
             md.riskRank = md.computeRiskRank
           })
         }
       }
   }
   
   
   
  //helper methods
   def colorColumn(cnt: Int, buffer: StringBuffer, value: String, colorStr: String) {

      buffer.append("<td bgcolor=")

      buffer.append(colorStr)
      buffer.append(">")
      buffer.append(value)
      buffer.append("</td>") 
    } 
   // for class risk reports 
   def getClassRisk : List[(Int, String, Set[String])] = {
      val clsTbl = Thread.currentThread().asInstanceOf[AnalysisHelperThread].classTable
      val rawRes =  clsTbl.foldLeft(List[(Int, String, Set[String])]())((res, kv) =>{
        val k = kv._1
        val clsDef = kv._2
        res::: List((clsDef.riskRank, clsDef.className, clsDef.allTaintKinds))  
      })
      val rawRes1 = rawRes.filter {
        case (n, name, _) => {
          n > 0
        }
      }
      rawRes1.sortBy {
        case (n, name, _) => -n
      }
   }
   
   // for methods risk reports: we will need its clsas name too
   def getMethodRisk : List[(Int, String, Set[String])] = {
      val clsTbl = Thread.currentThread().asInstanceOf[AnalysisHelperThread].classTable
      
      //get all the methods
      val allMethods = clsTbl.foldLeft(List[MethodDef]())((res, kv)=> {
        val clsDef = kv._2
        res ++ clsDef.methods
      })
      
      val allMethods2 = allMethods.filter(md => {md.riskRank > 0})
      val allMethods3 = allMethods2.sortBy( _.riskRank)(Desc)
      
      allMethods3.foldLeft(List[(Int, String, Set[String])]())((res, md) =>{ 
       res::: List((md.riskRank, md.methodPath, md.getAllTaintKinds))  
      })
   }
   
  
   
    def getStmtRisk : List[(Int, String, String, String, Set[String],String)] = {
      val clsTbl = Thread.currentThread().asInstanceOf[AnalysisHelperThread].classTable
      
      //get all the statment
      val allStmt = clsTbl.foldLeft(List[Stmt]())((res, kv)=> {
        val clsDef = kv._2
        res  ::: clsDef.getAllStmts
      })
      
      val allStmt2 = allStmt.filter(st => {st.riskRanking > 0})
      val allStmt3 = allStmt2.sortBy {
        case st => -st.riskRanking
      }//( _.riskRanking)(Desc)
      
      allStmt3.foldLeft(List[(Int, String, String, String, Set[String], String)]())((res, st) =>{  
        res ::: List((st.riskRanking, st.clsPath, st.methPath, st.lineNumber.toString, st.taintKind, st.toString))  
      })
   }
   
   ///// 
   def dumpClsRiskRanking(opts: AIOptions) { 
     
    
    var buffer = new StringBuffer()

    //title
    buffer.append("<html> <head>  <center> <title> Risk Ranking - Class Level</title> </center> </head> <h2>  Class Risk Ranking </h2><body> <table>\n")
    buffer.append("<tr ><td bgcolor=")
    buffer.append("#FFF6FA")
    buffer.append(">")
    // headers
    buffer.append("<b> Risk Score </b>")
    buffer.append("</td> <td >")
     buffer.append("<b> Categories </b>")
    buffer.append("</td> <td >")
    
    buffer.append("<b> Class </b>")
     buffer.append(" </td> </tr>")
 
    buffer.append("</br>")

    val clsRiskRes = RiskAnalysis.getClassRisk
    var cnt = 0
    //Just internating color
    clsRiskRes.foreach((rec) => {

      val (score, clsName, cates ) =  rec
       

      val colorStr = if (cnt % 2 == 0) {
        "FFFFFF" //white
      } else "#E8E8E8" // grey

        
      buffer.append("<tr >")
      colorColumn(cnt, buffer, score.toString, colorStr)
       colorColumn(cnt, buffer, StringUtils.getOneStringFromSetofString(cates), colorStr)
      colorColumn(cnt, buffer, clsName, colorStr)
      
      buffer.append("</tr>")

      cnt = cnt + 1
    })
    buffer.append("</table></body></html>")

    
    // file
    val reportDirName = opts.permReportsDirName //opts.apkProjDir + File.separator + statisticsDirName 
  
    val secuDir = new Directory(new File(reportDirName))
    if (!secuDir.exists) {
      secuDir.createDirectory(force = true)
      secuDir.createFile(failIfExists = false)
    }

      val path = opts.clsRiskRankingReportPath //stasticsDir + File.separator + CommonUtils.getStatisticsDumpFileName(opts) // or use opts.statsFilePath
     
      
      val file = new File(path)
      if (!file.exists()) {
        file.createNewFile()
      }
      val writer = new FileWriter(file)

      writer.write(buffer.toString)
      writer.close()

      println("Class Risk Ranking report dumped to: " + path)
      path
     
  }
   
   // extract the information to print out.
   // the statement should be those with risk ranks, either source or sink
   def  getStmtInfo(st: Stmt) : (String, String) = {
     st match {
      case   aas@AssignAExpStmt (lhReg, rhExp, nxt, ls , clsP, methP) => {
        if(aas.isConst) {
          ("", aas.getConstVals)
        }else ("", "") // not const asignaexpstmt
      }
      case  ids@InvokeDirectStmt(methoPath, _, _, _, _, _, _, _) 
      => {ids.getClassPathAndMethodName}
      case iis@ InvokeInterfaceStmt (_, _, _, _, _, _, _, _) 
      => {iis.getClassPathAndMethodName}
       
      case iss@InvokeSuperStmt(_, argRegAExp, objAExp, tyStrs, nxt, ls, clsP, methP) => {iss.getClassPathAndMethodName}
      case  iss@InvokeStaticStmt(_, _, _, _, _, _, _) => {iss.getClassPathAndMethodName}
      case is@InvokeStmt(_, _, _, _, _, _, _, _) => {is.getClassPathAndMethodName}
       // case field update/reference
      case _ =>   ("","")
     }
   
  }
   
    def getJsonOutput: List[JsonOutputForMethod] = {
     import org.ucombinator.utils.Int._
    // import org.ucombinator.utils.CommonUtils._
     
      val clsTbl = Thread.currentThread().asInstanceOf[AnalysisHelperThread].classTable
      
      //get all the methods
      val allMethods = clsTbl.foldLeft(List[(String,MethodDef)]())((res, kv)=> {
        val clsDef = kv._2
        
        res ++ clsDef.methods.map((md) => {(clsDef.className, md)})
      })
      
      val allMethods2 = allMethods.filter((rec)=> {
        val md  = rec._2
        md.riskRank > 0})
        
      val allMethods3 = allMethods2.sortBy( _._2.riskRank)(Desc)
      
      allMethods3.foldLeft(List[JsonOutputForMethod]())((res, rec) =>{ 
        /**
         * riskScore: Integer,
		methodName: String,
		fileName: String,
		className: String,
		shortDesc: String,
		longDesc: String,
		startLineNo: Integer,
		endLineNo:Integer,
		startCol: Integer
         */
        val clsName = rec._1
        val md = rec._2
        
        val lnStmts = md.getFlattenedBodyStmts.filter(
           (stmt)=> stmt match  {
          case ls@LineStmt(lnstr , nxt, ln, clsP, methP ) => {
           true
          } case _ => false
        })
        
        val lnNo = if(!lnStmts.isEmpty) {
          val fst =  lnStmts.first
          CommonUtils.getValue(fst.asInstanceOf[LineStmt].lnstr)
        } else 0
        
        val fileName = StringUtils.getFileNameFromClassPath(clsName)
     
        val methSouceLocation = new SourceLocationInfo(md.riskRank, 
         StringUtils.getMethNameFromMethPath(md.methodPath),
         fileName,
        StringUtils.classPathInDotFormat( clsName),
         CommonUtils.getStringFromSet(md.getAllTaintKinds), "",
         lnNo,
         0,
         0
         )
        
        val stmtsInMethod = md.getFlattenedBodyStmts
         val allStmt2 = stmtsInMethod.filter(st => {st.riskRanking > 0})
         val allStmt3 = allStmt2.sortBy {
         	case st => -st.riskRanking
      }
        
       val stmtSourceInfoList =  allStmt3.foldLeft(List[SourceLocationInfo]())((resSt, st) =>{  
           val lnSt = st.lineNumber
           
           val lnNO = lnSt match {
             case StmtNil => 0
             case _ => CommonUtils.getValue(lnSt.asInstanceOf[LineStmt].lnstr)
           }
             
          val (fld1, fld2) = getStmtInfo(st)
          
            
           val newStSrcLocInfo = new SourceLocationInfo(st.riskRanking, 
               fld2, // no need method name, or value in general
               st.toString(),// no need, file name
               fld1, //class name
               CommonUtils.getStringFromSet(st.taintKind), // shortscre
               "",
               lnNO,
               0,0
               )
           
        resSt ::: List(newStSrcLocInfo)  
      })
        
       res::: List(new JsonOutputForMethod(methSouceLocation, stmtSourceInfoList))  
      })
    
   }
    
    def dumpJsonOutput(opts:AIOptions) {
      
      val lstJsonMethod   = getJsonOutput
      
     val annotations = JsArray((lstJsonMethod map {
       (jsMethStmts) => {
         val methLevel = jsMethStmts.methodLevel
         val statms  = jsMethStmts.stmts
         
          JsObject("risk_score" -> JsNumber(methLevel.riskScore),
                    "method" -> JsString(methLevel.methodName),
                    "file_name" -> JsString(methLevel.fileName),
                    "class_name" -> JsString(methLevel.className),
                    "short_description" -> JsString(methLevel.shortDesc),
                    "long_description" -> JsString(methLevel.longDesc),
                    "start_line" -> JsNumber(methLevel.startLineNo),
                    "start_col" -> JsNumber(methLevel.startCol),
                    "sub_annotations" -> 
                    JsArray(statms.map((stmt) => {
                      JsObject("start_line" -> JsNumber(stmt.startLineNo),
                    		  "end_line" -> JsNumber(stmt.endLineNo),
                    		  "start_col" -> JsNumber(stmt.startCol),
                    		  "method" -> JsString(stmt.methodName),
                    		  "class_name" -> JsString(stmt.className),
                    		  "risk_score" -> JsNumber(stmt.riskScore),
                    		  "description" -> JsString(stmt.shortDesc))
                    }).toList))}}).toList)
      
      
      // file
    val reportDirName = opts.permReportsDirName //opts.apkProjDir + File.separator + statisticsDirName 
 
    val secuDir = new Directory(new File(reportDirName))
    if (!secuDir.exists) {
      secuDir.createDirectory(force = true)
      secuDir.createFile(failIfExists = false)
    }

      val path = opts.apkProjDir + File.separator + "reports" + File.separator + "riskrank.json" // or use opts.statsFilePath
     
      
      val file = new File(path)
      if (!file.exists()) {
        file.createNewFile()
      }
      val writer = new FileWriter(file)

      writer.write( JsObject("annotations" -> annotations).prettyPrint)
      writer.close()

      println("json risk rank report dumped to: " + path)
      path
    }
   
   
  
   
   def dumpMethRiskRanking(opts: AIOptions) {  
    
    var buffer = new StringBuffer()

    //title
    buffer.append("<html> <head>  <center> <title> Risk Ranking - Method Level </title> </center> </head> <h2>  Method Risk Ranking </h2><body> <table>\n")
    buffer.append("<tr ><td bgcolor=")
    buffer.append("#FFF6FA")
    buffer.append(">")
    // headers
    buffer.append("<b> Risk Score </b>")
    buffer.append("</td> <td >")
     buffer.append("<b> Categories </b>")
    buffer.append("</td> <td >")
    
    buffer.append("<b> Method Path </b>")
     buffer.append(" </td> </tr>")
 
    buffer.append("</br>")

    val methRiskRes = RiskAnalysis.getMethodRisk
    var cnt = 0
    //Just internating color
    methRiskRes.foreach((rec) => {

      val (score, meth, cates ) =  rec 

      val colorStr = if (cnt % 2 == 0) {
        "FFFFFF" //white
      } else "#E8E8E8" // grey

        
      buffer.append("<tr >")
      colorColumn(cnt, buffer, score.toString, colorStr)
       colorColumn(cnt, buffer, StringUtils.getOneStringFromSetofString(cates), colorStr)
      colorColumn(cnt, buffer, meth, colorStr)
      
      buffer.append("</tr>")

      cnt = cnt + 1
    })
    buffer.append("</table></body></html>")

    
    // file
    val reportDirName = opts.permReportsDirName //opts.apkProjDir + File.separator + statisticsDirName 
 
    val secuDir = new Directory(new File(reportDirName))
    if (!secuDir.exists) {
      secuDir.createDirectory(force = true)
      secuDir.createFile(failIfExists = false)
    }

      val path = opts.methRiskRankingReportPath //stasticsDir + File.separator + CommonUtils.getStatisticsDumpFileName(opts) // or use opts.statsFilePath
     
      
      val file = new File(path)
      if (!file.exists()) {
        file.createNewFile()
      }
      val writer = new FileWriter(file)

      writer.write(buffer.toString)
      writer.close()

      println("Clas Risk Ranking report dumped to: " + path)
      path
     
  }
   
   def dumpStmtRiskRanking(opts: AIOptions ) { 
     
     val clsTbl = Thread.currentThread().asInstanceOf[AnalysisHelperThread].classTable
    
    val sortedStmts = RiskAnalysis.getStmtRisk

    var buffer = new StringBuffer()

    //title
    buffer.append("<html> <head>  <center> <title> Risk Ranking - Statement Level </title> </center> </head> <h2> Statement Risk Ranking  </h2><body> <table>\n")
    buffer.append("<tr ><td bgcolor=")
    buffer.append("#FFF6FA")
    buffer.append(">")
    // headers
    buffer.append("<b> Risk Score </b>")
    buffer.append("</td> <td >")
     buffer.append("<b> Categories </b>")
    buffer.append("</td> <td >")
    
    buffer.append("<b> Class (file path) </b>")
    buffer.append("</td> <td >")
    // one column
    buffer.append("<b> Method </b>")
    buffer.append("</td> <td >")

    buffer.append("<b> Line Number </b>")
    buffer.append("</td> <td >")

    buffer.append("<b> Statement </b>")
    buffer.append(" </td> </tr>")
    buffer.append("</br>")

    var cnt = 0
    //Just internating color
     sortedStmts.foreach((ss) => {

      val (score, clsName, methName, lineNumber, cates, stmtStr) =  ss
      

      val colorStr = if (cnt % 2 == 0) {
        "FFFFFF" //white
      } else "#E8E8E8" // grey

        
      buffer.append("<tr >")
      colorColumn(cnt, buffer,  score.toString, colorStr)
       colorColumn(cnt, buffer, StringUtils.getOneStringFromSetofString( cates), colorStr)
      colorColumn(cnt, buffer, clsName, colorStr)
      colorColumn(cnt, buffer, methName, colorStr)
      colorColumn(cnt, buffer, lineNumber, colorStr)
      colorColumn(cnt, buffer, stmtStr, colorStr)
      buffer.append("</tr>")

      cnt = cnt + 1
    })
    buffer.append("</table></body></html>")

    
    // file
    val reportDirName = opts.permReportsDirName //opts.apkProjDir + File.separator + statisticsDirName 
 println("path is: ", reportDirName)
    val secuDir = new Directory(new File(reportDirName))
    if (!secuDir.exists) {
      secuDir.createDirectory(force = true)
      secuDir.createFile(failIfExists = false)
    }

      val path = opts.riskRankingReportPath //stasticsDir + File.separator + CommonUtils.getStatisticsDumpFileName(opts) // or use opts.statsFilePath
     
      
      val file = new File(path)
      if (!file.exists()) {
        file.createNewFile()
      }
      val writer = new FileWriter(file)

      writer.write(buffer.toString)
      writer.close()

      println("Statement Risk Ranking report dumped to: " + path)
      path
     
  }
   
   // dump report 
     def dumpPreRiskRanking(opts: AIOptions) {
       RiskAnalysis.dumpClsRiskRanking(opts)
       RiskAnalysis.dumpMethRiskRanking(opts)
       RiskAnalysis.dumpStmtRiskRanking(opts)
     }
   
   
   
  
}