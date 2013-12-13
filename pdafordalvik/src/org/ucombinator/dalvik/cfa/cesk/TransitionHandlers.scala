package org.ucombinator.dalvik.cfa.cesk

import org.ucombinator.dalvik.syntax._
import org.ucombinator.utils._
import org.ucombinator.dalvik.specialAPIs.RawStringLibsAI
import org.ucombinator.dalvik.specialAPIs.ExternalLibCallsHandler
import org.ucombinator.dalvik.exceptionhandling.ExceptionHandling
import org.ucombinator.dalvik.statistics.Statistics
import org.ucombinator.dalvik.informationflow.DalInformationFlow
import org.ucombinator.playhelpers.AnalysisHelperThread
import org.ucombinator.utils.ForIntentFuzzerUtil

/**
 * Part of the logic from StackCESK is extracted to this trait
 */
trait TransitionHandlers extends //StateSpace with 
ExternalLibCallsHandler with ExceptionHandling with StmtForEqual{
  
  import org.ucombinator.domains.CommonAbstractDomains._
  
  private def resolveMethod(invokeType: String, oClsName: String, methPath: String, argTypes: List[String]) 
  : List[MethodDef] =  {
    invokeType match {
      //direct
      case "direct" => {
       // println("className:" + oClsName, " methPath: "+ methPath)
      DalvikClassDef.lookupMethod(oClsName, methPath, argTypes, 1)
      }
        // virtual 
      case "virtual" => {
      DalvikClassDef.lookupMethod(oClsName, methPath, argTypes, 2)
      }
      
      //interface
      case "interface" => { 
        DalvikClassDef.lookUpInterfaceMethod(oClsName, methPath, argTypes)}
      
      // super
      case "super" =>{
        DalvikClassDef.lookupMethod(oClsName, methPath, argTypes, 4)
      }
    }
  }
  
  
  def handleNonStaticInvoke(
      invokeType: String,
      ivkS: AbstractInvokeStmt,
      methPath:String, 
      realN: Stmt,
      objExp: AExp, // from pattern mathcin
      objAexp: AExp,
      argRegExps: List[AExp],
      ls: Stmt,
      fp:FramePointer, 
      tyStrs: List[String],
      tp: Time,
      s: Store, 
      pst:PropertyStore,
      kptr: KAddr, 
      t:Time, 
      k:Kont,
      stForEqual: StForEqual,
      pstate : PartialState ) : Set[Conf] ={
        val possibleValues = atomEval(objAexp, fp, s)
        val objVals = filterObjValues(possibleValues,s)
       Statistics.recordCallObjs(buildStForEqual(ivkS), objVals.toList.map(_.toString))
       Statistics.countReachableMethodCalls(objVals.toList)
       // for intent fuzzer
       if(Thread.currentThread().asInstanceOf[AnalysisHelperThread].gopts.forIntentFuzzer){
        val argVals = argRegExps map (atomEval(_, fp, s))
        
         if(argVals.length > 0) {
           val objVal = argVals.head
          val strKeys = getPossibleStrings(objVal, s)
          val argTypeVals = getTypeAndValForArgs(tyStrs, argVals, s)
           ForIntentFuzzerUtil.decideIntentFields(methPath, stForEqual.clsPath, stForEqual.methPath, ivkS, argTypeVals)
          // setIntentOpsInfoInState(pstate, methPath, stForEqual.clsPath, stForEqual.methPath, ivkS, argTypeVals)
          // println("pstate intent info: ",pstate.intentRelatedInfo )
         }
         
         else{
           // empty domain
          // setIntentOpsInfoInState(pstate, methPath, stForEqual.clsPath, stForEqual.methPath, ivkS, Set[(String, Set[String])]())
            ForIntentFuzzerUtil.decideIntentFields(methPath, stForEqual.clsPath, stForEqual.methPath, ivkS, Set[(String, Set[String])]())
            //println("pstate intent info: ",pstate.intentRelatedInfo )
         }
        
       }
           /**
         * It is possible that the objVals are empty!
         */
         if (objVals.isEmpty) {
           if( !AnalysisScope.isExclusive(methPath)){
        	 NonNullUtils.trackNullRefDuringConstrs(ivkS)
        	// println("EMPTY OBJTS" + methPath)
           }
          Set((PartialState(buildStForEqual(realN ), fp, s, pst, kptr, tp), k))
        } 
          
        /**
       * if the object register found values, we should move on
       */ else {
          // the external library that we're actually dealing with
          /*if (isExternalLibCalls(methPath)) {
            handleExternalLibCalls(methPath, ivkS, argRegExps, objVals, ls, s, pst, realN, fp, kptr, t, tp, k,stForEqual )
          } */
         if (isExternalLibCalls(methPath)) {
            handleExternalLibCalls(methPath, ivkS, argRegExps, List(objAexp), objVals, ls, s, pst, realN, fp, kptr, t, tp, k,stForEqual )
          } 
        
            /**
          *  we just keep on analysing if the other external libraries that we're not actually interprete it.
          *  The following branch also includes the calls taht we can get its source
          */ else {
            val clsName = StringUtils.getClassPathFromMethPath(methPath) // class name from meth path
            //actually the className can be extracted from the call method Name.
            // val 
            objVals.toList.foldLeft(Set[Conf]())((stateSet, curObjValV) => {
              val curObjVal = curObjValV.asInstanceOf[AbstractObjectValue]
              val oClsName = classTypeOfAbsObject(curObjVal) // objectClassname
             
              val resolvedMethds = resolveMethod(invokeType, oClsName, methPath, tyStrs)// DalvikClassDef.lookupMethod(oClsName, methPath, tyStrs, false)
           
              resolvedMethds match {
                case Nil => {
                  // Continue -- fake successors 
                  stateSet + ((PartialState(buildStForEqual(realN), fp, s, pst, kptr, tp), k))
                }
                case hd :: tl => {
                  // if more than one method were resovled, we just use the head.
                  //  BUT it is supposed to get only one back
                  //("@InvokeStmt: ", ivkS)
                  /**
                   * Here we're going to inject fault states if there are any, if the method declares throws exceptions
                   */
                  //println("current found method in invokeStmt:", hd.methodPath)
                  val liveRegs =  Thread.currentThread().asInstanceOf[AnalysisHelperThread].liveMap.getOrElse(buildStForEqual(realN), Set())
                  // val injStates = getInjectStatesFromAnnotations( hd.localHandlers, hd.annotationExns, fp, s, k, t, ivkS, "", "", kptr, liveRegs)
                  /**
                   * We are returning normal states well as the injected states
                   */
                 
                  stateSet ++
                    applyMethod(stForEqual, false, hd.body, hd.regsNum, Some(curObjVal.asInstanceOf[ObjectValue]), fp, s, pst, k, List(objAexp), argRegExps, List(), t, ivkS, realN, kptr) //++  injStates
                }
              }
            })
          }
        }
  }
 
 
  /**
   * apply method will resolve the formal parameter registers for the call.
   * it will  allocate the new framepointer
   * form bindings between formal parameter registers and the argument values
   * forms the continuation
   * In addition, as the handleExternalLibcalls, for the security propataion
   * it will propagate the property values of the arguments to the formal parameters.
   * and refining the security value of the object that the method is invoked on.
   * it will also bind the values of teh joined property values to the move-result target
   * working around directly waiting the "ret" <- XXX, and "YYY" <- "ret"
   */
  def applyMethod(stForEqual: StForEqual, isEntryApply: Boolean, methBody: Stmt, regsNum: BigInt, ovO: Option[ObjectValue], fp: FramePointer, 
		  		s: Store, pst: PropertyStore, k: Kont, objExps: List[AExp], argsRegExps: List[AExp], argsTopVal: List[D], 
		  		t: Time, st: Stmt, callerNxtSt: Stmt, kptr: KAddr): Set[Conf] = {
   // println("in ApplyMethod " + stForEqual.oldStyleSt)
    val tp = tick(List(st), t)
    val calleeBodyStmt = methBody
    val theNext = CommonUtils.findNextStmtNotLineOrLabel(calleeBodyStmt)
   // println("method body is Stmt: " +theNext)
    /* val theNext =
       theNext1 match{
      case StmtNil => {
        new ReturnStmt(RegisterExp(SName.from("")), StmtNil, StmtNil, stForEqual.clsPath, stForEqual.methPath)
      }
      case _  => theNext1
    }*/
    
    //println("@apply method callee's: ", theNext)
    val newFP = fp.push(t, st)
    val funk = new FNKFrame(callerNxtSt, fp)
    Debug.prntDebugInfo("@apply method caller's next: ", callerNxtSt) 
    
    val argRegAddrs = argsRegExps.map((argExp) => {fp.offset(getRegExpStr(argExp))}) 
    
        
     val pstMoveResult=  propagateTaintPropertyForFunctionInvokeObjAndMoveResult(argRegAddrs, objExps, pst , st , callerNxtSt , stForEqual.clsPath,  stForEqual.methPath, stForEqual.lineSt,  fp )
          // the property of the arguments are properagated too 
     
      val argExpStrs = argsRegExps map (getRegExpStr)
      val argSecurityVals = argExpStrs.map((argRegStr) => {storeLookup(pst, fp.offset(argRegStr))}) 
     
      //println(isEntryApply)
    if (!isEntryApply) { // normal aplpy
       
      val argVals = argsRegExps map (atomEval(_, fp, s))
      
      
      val startingIndex = regsNum - argsRegExps.length

      val formalRegStrOffsets = List.range(startingIndex, regsNum) map (StringUtils.constrRegStr)
      val formalRegOffsetAddrs = formalRegStrOffsets map (newFP.offset(_))
      val bindings = formalRegOffsetAddrs.zip(argVals) 
      
   
      val newStore = storeUpdate(s, bindings) 
   
      val propertyBindings = formalRegOffsetAddrs.zip(argSecurityVals)
      val newPStore = storeUpdate(pstMoveResult, propertyBindings)
      
      //to get the joined security values from property store  
      // just in case objExps can be called from static invoke, which can be empty 
      
      val pv = 
        if(objExps.isEmpty) // the it is from static invoke which does not have address
        	s.mkDomainD()//Set[Value]()
        else // then there gonna be one object AExp // the newly joined values of the obejct property value
        	storeLookup(pstMoveResult, fp.offset(objExps.head.toString))
      
      ovO match {
        case Some(ov) => {
          val thisRegum = startingIndex - 1
          Debug.prntDebugInfo("the new this  reg str: regsnum is" + regsNum + " the argsExp.elgnth," + argsRegExps.length, thisRegum)
          val thisRegStr = StringUtils.constrRegStr(thisRegum)
          val thisRegAddr = newFP.offset(thisRegStr)
          Debug.prntDebugInfo("the this reg offset is", thisRegAddr)
          
          
          val newStore2 = //storeStrongUpdate(newStore, List((thisRegAddr, newStore.mkDomainD(ov) ))) 
           storeUpdate(newStore, List((thisRegAddr, newStore.mkDomainD(ov) ))) 
          //the property of the arguments are propagated to formal parameters
          val newPStore2 = storeUpdate(newPStore, List((thisRegAddr, pv))) 
          
          val newState = //(PartialState(buildStForEqual(theNext ), newFP, newStore2,   newPStore2,
             // kptr, tp), funk :: k)
          
            // intra-procedrual: old fp and continuation
           if( Thread.currentThread().asInstanceOf[AnalysisHelperThread].gopts.intraprocedural)   {
             (PartialState(buildStForEqual(callerNxtSt ), fp, newStore2,   newPStore2,
              kptr, tp),  k)
           }
          // inter-procedrual
           else{
             (PartialState(buildStForEqual(theNext ), newFP, newStore2,   newPStore2,
              kptr, tp), funk :: k)
           }
        	   Set(newState)
        }
        
        case None => { //static methods invocation!!!
           if( Thread.currentThread().asInstanceOf[AnalysisHelperThread].gopts.intraprocedural)   {
             Set((PartialState(buildStForEqual(callerNxtSt ), fp, newStore,  newPStore, 
            kptr, tp),   k))
           }else{
             Set((PartialState(buildStForEqual(theNext ), newFP, newStore,  newPStore, 
            kptr, tp), funk :: k))
           }
          
        }
      }

    } else { // if it is entry apply,  no need to propagate the security property
      
      val argVals = argsTopVal 
      val startingIndex = regsNum - argVals.length
      val formalRegStrOffsets = List.range(startingIndex, regsNum) map (StringUtils.constrRegStr)
      val formalRegOffsetAddrs = formalRegStrOffsets map (newFP.offset(_))
      val bindings = formalRegOffsetAddrs.zip(argVals)
      val newStore = storeUpdate(s, bindings) 
       
      val propertyBindings = formalRegOffsetAddrs.zip(argSecurityVals)
      val newPStore = storeUpdate(pstMoveResult, propertyBindings)
       val pv = 
        if(objExps.isEmpty) // the it is from static invoke which does not have address
        	s.mkDomainD()//Set[Value]()
        else // then there gonna be one object AExp // the newly joined values of the obejct property value
        	storeLookup(pstMoveResult, fp.offset(objExps.head.toString))
   
      ovO match {
        case Some(ov) => {
          val thisRegum = startingIndex - 1
          Debug.prntDebugInfo("the new this  reg str: regsnum is" + regsNum + " the argsExp.elgnth," + argsRegExps.length, thisRegum)
          val thisRegStr = StringUtils.constrRegStr(thisRegum)
          val thisRegAddr = newFP.offset(thisRegStr)
          Debug.prntDebugInfo("the this reg offset is", thisRegAddr)
          val newStore2 = storeUpdate(newStore, List((thisRegAddr, s.mkDomainD(ov)  )))
          val newPStore2 = storeUpdate(newPStore, List((thisRegAddr, pv)))
          val newState = (PartialState(buildStForEqual(theNext ), newFP, newStore2,  newPStore2, 
              kptr, tp), funk :: k)
          Set(newState)
        }
        case None => Set((PartialState(buildStForEqual(theNext ), newFP, newStore,  newPStore, 
            kptr, tp), funk :: k))
      }
    }

  }

  /**
   * For int, long, or float, or double, we all abstract as Num. But it causes some control states merges.
   * but this is not precise. XXX TODO: to add support for other types
   * currently for integer type, if value is greater than 2, then we jump to top
   * for any operation of number, we jump to the top
   * which means that the val for all the numerical operations is condensed to NumTop
   *
   */
  def applyAtomicOp(stForEqual: StForEqual, opCode: SName, opCodeType: String, lineNo: Stmt, destReg: RegisterExp, aExps: List[AExp], 
		  			s: Store, 
		  			pst: PropertyStore, nxt: Stmt, fp: FramePointer, kptr: KAddr, tp: Time, k: Kont): Set[Conf] = {

    Debug.prntDebugInfo("@Applyautomatic Sname:  " + opCode.toString() + ":destREgExp:" + destReg + "The destAddress is: ", fp.offset(destReg.regStr))
    opCodeType match {
      case "number" =>
        { val absValue = 
          if(isConstNum(opCode)) {
             if(aExps.length ==1) {
               aExps.head match {
                 case si@IntExp(n) =>{mkNumLit(si.numVal)}
                 case _ => {throw new CESKException("not IntExp in isConstNum"+ opCode.toString()+ lineNo.toString())}  
                 }
               }
              else throw new CESKException("the const num's operators lengh is not 1")
             }
          else NumTop
          val newStore = storeUpdate(s, List((fp.offset(destReg.regStr), s.mkDomainD(absValue) )))
          
          val newState = (PartialState(buildStForEqual(nxt), fp, newStore, pst, kptr, tp), k)
          Set(newState)
        }
      case "check-cast" => {
       // println("checkCast!!!", stForEqual)
        //println("checkCAstNExt: ", nxt)
        if (aExps.length == 1) {
          aExps.head match {
            case se @ RegisterExp(_) => {
              val clsName = se.regStr
               val castTypeStr = StringUtils.getTypeFromObjectWrapper(clsName)
               val newOP = ObjectPointer(tp, clsName, lineNo)
              /**
               * we don't know the class name is some kind of sensitve sources or not
               */
              val sourceOrSinkLevel = DalInformationFlow.decideSourceOrSinkLevel(castTypeStr)
              val newPStore = 
                if(sourceOrSinkLevel>0) {
                 // val securityValue = SecurityValue(stForEqual.clsPath, stForEqual.methPath, stForEqual.lineSt, castTypeStr, sourceOrSinkLevel)
                  val secuVals = genTaintKindValueFromStmt(stForEqual.oldStyleSt, s)
                  val pst2 =  storeUpdate(pst, List((fp.offset(destReg.regStr), secuVals)))
                   initObjectProperty(castTypeStr ,  pst2 ,
                       newOP, secuVals)
                }else{
                  pst
                }
              
             
              val noChange = DalvikClassDef.isInterface(castTypeStr)
              
              if (noChange) {
                // no change to the values we go on
                Set((PartialState(buildStForEqual(nxt), fp, s,  newPStore, 
                    kptr, tp), k))
              } else { // should probably be a class name defined? or library class?
                // we strong udpate to get new one usually the case do strong udpate
                
                val objVal = ObjectValue(newOP, clsName)

                val newStore = //storeStrongUpdate(s, List((fp.offset(destReg.regStr), s.mkDomainD(objVal))))
                  storeUpdate(s, List((fp.offset(destReg.regStr), s.mkDomainD(objVal))))
                // initialize the fields of the currnet class and return new store?
                val newStore2 = initObject(castTypeStr, newStore, newOP)
                //field area is propagated with hte sources from the object
              
                val newState = (PartialState(buildStForEqual(nxt), fp, newStore2,  newPStore, 
                    kptr, tp), k)
                Set(newState)
                // Set((PartialState(buildStForEqual(nxt ), fp, s, kptr, tp), k))
              } 
           /*      val newStore = storeUpdate(s, List((fp.offset(destReg.regStr), Set(absStr))))
          val newState = (PartialState(nxt, fp, newStore, kptr, tp), k)
          Set(newState)*/
             }
             case _ => throw new SemanticException("The const-class's class name is not parsed as RegisterExp")
           }
         }     else throw new SemanticException("\nThe const-class = 's string exp is not 1. It is of length" + aExps.length)
      }
      
      case "string" => {
        // if it is the string, then hte aExps should be length of 1
       
        if (aExps.length == 1) {
          aExps.head match {
            /**
             *  for string instantiation. there will be an string object allocated in the heap,
             *  with the field value as the abstracted string value
             */
            case se @ StringLitExp(_) => {
              val absValue = StringLit(se.strLit)
              val objPointer = ObjectPointer(tp,  StringUtils.getStringType, lineNo)
              val objVal = ObjectValue(objPointer, StringUtils.getStringType)
              val curStrAddr = objPointer.offset("value")
              
              val newStore = storeStrongUpdate(s, List((fp.offset(destReg.regStr), s.mkDomainD(objVal))))
              val newStore2 = storeStrongUpdate(newStore, List((curStrAddr, s.mkDomainD(absValue)))) 
             //    println("the newPStore in const string")
              val newPStore = propagatePStore(pst, se.strLit , stForEqual ,  List(fp.offset(destReg.regStr), curStrAddr), true )  
            
              
              Set((PartialState(buildStForEqual(nxt ), fp, newStore2,  newPStore, 
                  kptr, tp), k))
              
              /* val newStore = storeUpdate(s, List((fp.offset(destReg.regStr), Set(absValue))))
          val newState = (PartialState(nxt, fp, newStore, kptr, tp), k)
          Set(newState)*/
            }
            case _ => throw new SemanticException("The const-string = 's string is not parsed as StringLitExp")
          }
        } else throw new SemanticException("\nThe const-string = 's string exp is not 1. It is of length" + aExps.length)
      }
      /**
       * deal with const class specially
       */
      case "class" => {
        /**
         * the length gotta ba 1 for const-class 
         * the destreg is the class reference
         * the class reference has a field of name, mapped to the string reference
         * the string reference has a field of value, which is the abstract vlaue 
         * of the class
         */
         if (aExps.length == 1) {
           aExps.head match {
             case se@RegisterExp(_) => {
               val clsName = se.regStr
               val absStr =StringLit(clsName)
               val objPointerCls = ObjectPointer(tp, "java/lang/Class", lineNo)
               val objPointerStr = ObjectPointer(tp, "java/lang/String", lineNo)
               val objValCls = ObjectValue(objPointerCls, "java/lang/Class")
               val objValStr = ObjectValue(objPointerStr, "java/lang/String")
               val clsFieldAddr = objPointerCls.offset("name")
               val strValueAddr = objPointerCls.offset("value")
               val newStore = storeUpdate(s, List(
                   (fp.offset(destReg.regStr), s.mkDomainD(objValCls)), 
                   (clsFieldAddr, s.mkDomainD(objValStr)), 
                   (strValueAddr, s.mkDomainD(absStr)))) 
                val newPStore = 
                   propagatePStore(pst, se.regStr , stForEqual ,  List(fp.offset(destReg.regStr), clsFieldAddr,strValueAddr ) , false)  
               Set((PartialState(buildStForEqual(nxt ), fp, newStore,   newPStore, 
                   kptr, tp), k))
           /*      val newStore = storeUpdate(s, List((fp.offset(destReg.regStr), Set(absStr))))
          val newState = (PartialState(nxt, fp, newStore, kptr, tp), k)
          Set(newState)*/
             }
             case _ => throw new SemanticException("The const-class's class name is not parsed as RegisterExp")
           }
          }
          else throw new SemanticException("\nThe const-class = 's string exp is not 1. It is of length" + aExps.length)
        
      }
    }

  }

  /**
   * move move/from16, move-wide, move-wide/16. move-wide/from16, move-object, move-object/from16, move-object/16
   */
  def handleMove(destReg: RegisterExp, aExps: List[AExp], s: Store, pst:PropertyStore,  nxt: Stmt, fp: FramePointer, kptr: KAddr, tp: Time, k: Kont): Set[Conf] = {
    val destAddr = fp.offset(destReg.regStr)
    assert(aExps.length == 1)
    val srcReg = aExps.head
    val srcRegExp = srcReg match {
      case RegisterExp(sv) => srcReg.asInstanceOf[RegisterExp]
      case _ => { throw new SemanticException("the src register in move statement is not RegisterExp" + srcReg.toString()) }
    }
    val newVal = storeLookup(s, fp.offset(srcRegExp.regStr))
    val newStore = storeUpdate(s, List((destAddr, newVal)))
     
    val newPVal = storeLookup(pst, fp.offset(srcRegExp.regStr))
    val newPStore = storeUpdate(pst, List((destAddr, newPVal)))
    
    val newState = (PartialState(buildStForEqual(nxt) , fp, newStore,  newPStore, 
        kptr, tp), k) 
    Set(newState)
  }

  def handleAExpAssign(sft: StForEqual, assignS: AssignAExpStmt, lhReg: AExp, rhExp: AExp, s: Store, pst: PropertyStore, realN: Stmt, fp: FramePointer, kptr: KAddr, tp: Time, k: Kont): Set[Conf] = {

    val destReg =
      lhReg match {
        case RegisterExp(sv) => {
          lhReg.asInstanceOf[RegisterExp]
        }
        case _ => {
          throw new SemanticException("ASsignAexpStmt: left hand side is not  RegisterExp" + lhReg.toString())
        }
      }
    rhExp match {
      case RegisterExp(sv) => { // should be move-result
       

        val srcReg = rhExp.asInstanceOf[RegisterExp]
        val srcVal = atomEval(srcReg, fp, s) 
        val secuVals = storeLookup(pst, fp.offset(srcReg.toString))
        val newStore = storeUpdate(s, List((fp.offset(destReg.regStr), srcVal))) 
        val newPStore = storeUpdate(pst, List((fp.offset(destReg.regStr), secuVals)))
        
        
        val newState = (PartialState(buildStForEqual(realN ), fp, newStore, newPStore, 
            kptr, tp), k)
        Set(newState)
      }

      case AutomicOpExp(opCode, aExps @ _*) => {
        if (isPrimitiveNumerical(opCode)) {
          Debug.prntDebugInfo("@AssignAExpStmt:Automic " + opCode.toString(), assignS)
          Debug.prntDebugInfo("Next is: ", assignS.next)
          applyAtomicOp(sft, opCode, "number", assignS.lineNumber, destReg, aExps.toList, s, pst, realN, fp, kptr, tp, k)
        } else if (isConstString(opCode)) {
          applyAtomicOp(sft, opCode, "string", assignS.lineNumber, destReg, aExps.toList, s, pst,realN, fp, kptr, tp, k)
        } else if (isConstClass(opCode)) {
          applyAtomicOp(sft, opCode, "class", assignS.lineNumber, destReg, aExps.toList, s, pst, realN, fp, kptr, tp, k)
        } else if(isCheckCast(opCode)){
          applyAtomicOp(sft, opCode, "check-cast", assignS.lineNumber, destReg, aExps.toList, s, pst, realN, fp, kptr, tp, k)
        }else if (isMove(opCode)) {
          handleMove(destReg, aExps.toList, s, pst , realN, fp, kptr, tp, k)
        } else {
          throw new SemanticException(" AutomicOpExp: not promitive numerical, not move, what is that->" + opCode.toString())
        }
      }
      // otherwise, it will be in the aExps
      case _ => throw new SemanticException("ASsignAexpStmt: right hand side can't be RegisterExp and AutomicOpExp" + rhExp.toString())
    }
  }

  /**
   * for packed/sparse-wtich do we need to add the next statement?
   */

  def handleSwitch(pswS: SwitchStmt, lblStrs: List[AExp], nxt: Stmt, fp: FramePointer, s: Store, pst: PropertyStore, kptr: KAddr, t: Time, k: Kont): Set[Conf] = {
    Debug.prntDebugInfo("the labels parse to handler: ", lblStrs)
    Thread.currentThread().asInstanceOf[AnalysisHelperThread].gopts.brNum += 1
    
    val (stmtSSE, stmtSSNoE) = pswS.getBranchStmts

    val  branches  = stmtSSE.foldLeft(Set[Conf]())((res, ss) => {
      res ++ Set((PartialState(buildStForEqual(ss ), fp, s, pst, kptr, t), k))
    })
    
     if(Thread.currentThread().asInstanceOf[AnalysisHelperThread].gopts.obranches) {
       if(Thread.currentThread().asInstanceOf[AnalysisHelperThread].gopts.brNum > Thread.currentThread().asInstanceOf[AnalysisHelperThread].gopts.brCutoff)
    	   Set((PartialState(buildStForEqual(nxt ), fp, s, pst, kptr, t), k))
    else  branches ++ Set((PartialState(buildStForEqual(nxt ), fp, s, pst, kptr, t), k))
     }
     else{
        branches ++ Set((PartialState(buildStForEqual(nxt ), fp, s, pst, kptr, t), k))
     }
   
  }

  def handleFieldAssign(fieldS: FieldAssignStmt, s: Store, pst: PropertyStore, nxt: Stmt, fp: FramePointer, kptr: KAddr, tp: Time, k: Kont): Set[Conf] = {
    fieldS match {
      // igetfield
      case FieldAssignStmt(destReg: RegisterExp, fieldExp: NonStaticFieldExp, _, _, _,_) => {
        Debug.prntDebugInfo("@igetfield ", fieldS)
        fieldAssignHandleHelperI(true, fieldS, fieldExp, destReg, s, pst, nxt, fp, kptr, tp, k)
      }
      // iputfield
      case FieldAssignStmt(fieldExp: NonStaticFieldExp, srcReg: RegisterExp, _, _, _,_) => { 
    	// println("iputField!!!" , fieldS)
        fieldAssignHandleHelperI(false, fieldS, fieldExp, srcReg, s, pst, nxt, fp, kptr, tp, k)
      }
      // Sgetfield 
      case FieldAssignStmt(destReg: RegisterExp, fieldExp: StaticFieldExp, _, _,_,_) => {
        Debug.prntDebugInfo("@Sgetfield ", fieldS)
        fieldAssignHandleHelperS(true, fieldS , fieldExp, destReg, s, pst, nxt, fp, kptr, tp, k)
      }
      // Sputfield
      case FieldAssignStmt(fieldExp: StaticFieldExp, srcReg: RegisterExp, _, _, _,_) => {
        Debug.prntDebugInfo("@Sputfield ", fieldS)
        fieldAssignHandleHelperS(false,  fieldS ,fieldExp, srcReg, s, pst, nxt, fp, kptr, tp, k)
      }
      case _ => {
        throw new SemanticException("the field assginment statement type error!" + fieldS.toString())
      }
    }
  }

  /**
   * the address for the static field is just the offset based on the framepoint
   * it has no object pointer, because static fields are just class fields.
   */
  def fieldAssignHandleHelperS(isStaticGet: Boolean, fieldS: FieldAssignStmt, fieldExp: StaticFieldExp, srcOrDestReg: RegisterExp, s: Store, 
      pst: PropertyStore, nxt: Stmt, fp: FramePointer, kptr: KAddr, tp: Time, k: Kont): Set[Conf] = {

    val staticFieldAddr = fp.offset(fieldExp.fp)
    val srcOrDestRegAddr = fp.offset(srcOrDestReg.regStr)

    if (isStaticGet) {
      val fieldVals = storeLookup(s, staticFieldAddr)
      val fieldPropertyVals = storeLookup(pst, staticFieldAddr)
      Debug.prntDebugInfo("the  get from field", fieldVals)
      val newStore = storeUpdate(s, List((srcOrDestRegAddr, fieldVals)))
      val newPStore = 
        // if it is a source or sink, we will record it in the taint store and be propagated
      if(fieldS.sourceOrSink > 0) {
        storeUpdate(pst, List((srcOrDestRegAddr, genTaintKindValueFromStmt(fieldS,s))))
      }
      else
       storeUpdate(pst, List((srcOrDestRegAddr, fieldPropertyVals))) 
       
      val newState = (PartialState(buildStForEqual(nxt ), fp, newStore, newPStore, 
          kptr, tp), k)
      Set(newState)
    } else {
      val srcRegVals = storeLookup(s, srcOrDestRegAddr)
      val srcRegPropertyVals = storeLookup(pst, srcOrDestRegAddr)
      val newStore = storeUpdate(s, List((staticFieldAddr, srcRegVals)))
      val newPStore = storeUpdate(pst, List((staticFieldAddr, srcRegPropertyVals))) 
      val newState = (PartialState(buildStForEqual(nxt ), fp, newStore,  newPStore,
          kptr, tp), k)
      Set(newState)
    }
  }

  def classTypeOfAbsObject(aob: AbstractObjectValue) : String = {
    aob match{
              case objv@ObjectValue(_,_) => {
                //println("ObjValue ", objv.className)
                objv.className}
              case objT@ObjectSomeTop(_) => {
               // println("ObjValueT ", objT)
                objT.className}
            }
  }
  /**
   * for iget and iput
   */

  def fieldAssignHandleHelperI(isInstanceGet: Boolean, fieldAssignStmt: FieldAssignStmt, fieldExp: NonStaticFieldExp, srcOrDestReg: RegisterExp, 
      s: Store, pst: PropertyStore, nxt: Stmt, fp: FramePointer, kptr: KAddr, tp: Time, k: Kont): Set[Conf] = {
    
    val objRegExp = fieldExp.objExp
    val fieldPath = fieldExp.fp

  //  println(fieldExp)
    val (fieldClsPath, fieldName) = StringUtils.getClsPathAndFldNameFromFieldPath(fieldPath)

    val objRegE = objRegExp match {
      case RegisterExp(sv) => objRegExp.asInstanceOf[RegisterExp]
      case _ => { throw new SemanticException("the obj register in instance get statement is not RegisterExp" + objRegExp.toString()) }
    }
    val vals = storeLookup(s, fp.offset(objRegE.regStr))
    val objVals = filterObjValues(vals, s)

    if(objVals.isEmpty) {
      if(isInstanceGet){ 
        NonNullUtils.trackNullRefDuringConstrs(fieldAssignStmt)
      }
        Set ((PartialState(buildStForEqual(nxt ), fp, s, pst, kptr, tp), k))
    } else {
    
    objVals.toList.flatMap((objVv) => {
      
      val objV = objVv.asInstanceOf[ObjectValue]
      val objPointer = objV.op
      val objType = objV.className

  /*    *//**
       * resolving the fieldPaths
       * jsut leave it here, no small helpers.
       *//*
      val newFieldPath =
        if (objType == fieldClsPath) {
          fieldPath
        } else {
          val objCls = DalvikClassDef.forName(objType)
          val supersOfCurObj = objCls match {
            case Some(ocls) => {
              ocls.getSupersStr(List())(objType)
            }
            case None => {
              List()
            }
          }
         println("Supers of Cur Obj: " + objType + " for fieldClasPAth: " + fieldClsPath , supersOfCurObj)
          
          if (supersOfCurObj.contains(fieldClsPath)) {
            StringUtils.getDistinctMethodOrFieldPath(objType, fieldName, "fld")
          } else 
          { println("fieldClasPath: ", fieldClsPath)
            println(supersOfCurObj)
            throw new SemanticException("the field class path in iget/iput is not equal to the obj register's type, and it is not super type" ) }
        }
      Debug.prntDebugInfo("The new fieldPath is " + newFieldPath, "the field class type: " + fieldClsPath + " " + "the objtType : " + objType)
*/
      val srcOrDestAddr = fp.offset(srcOrDestReg.regStr)
      val fieldAddr = objPointer.offset(fieldName)
       val objectPropertyVals = storeLookup(pst, fp.offset(objRegE.regStr))
       
      if (isInstanceGet) {
        
        val fieldVals = storeLookup(s, fieldAddr)
      
        val newStore = storeUpdate(s, List((srcOrDestAddr, fieldVals)))
        
        // the joined values of the fields as well as the object addr will be propergated to the iget target
        val fieldPropertyVals = storeLookup(pst, fieldAddr)
       
        
        val joinedPropertyVals = fieldPropertyVals join objectPropertyVals
        
        val newPStore = storeUpdate(pst, List((srcOrDestAddr, joinedPropertyVals)))
        
        val newState = (PartialState(buildStForEqual(nxt ), fp, newStore,   newPStore, 
            kptr, tp), k) 
        List(newState)
        
      } else 
      { 
        val newValtoPut = storeLookup(s, srcOrDestAddr)
       // println("srcOrDestAddr: " + srcOrDestAddr)
       // println("---looked vals ", newValtoPut)
       // println("----to fiedAddr", fieldAddr)
        val newStore = storeUpdate(s, List((fieldAddr, newValtoPut))) 
        
        val newPropertyValToPut = storeLookup(pst, srcOrDestAddr)
        
        // will propagate the object and the register property vlaues into the fields
        val joinedPropertyValss = newPropertyValToPut join objectPropertyVals
        
        val newPStore = storeUpdate(pst, List((fieldAddr, joinedPropertyValss)))
            
        val newState = (PartialState(buildStForEqual(nxt ), fp, newStore,  newPStore, 
            kptr, tp), k) 
        
        List(newState)
      }
    }).toSet
    }
  }
  
   //private def recordIntentOpsInfoInState(state: ControlState, )
   def setIntentOpsInfoInState(state: PartialState, mp: String, clsPath: String, enclosingMethPath: String, stmt: Stmt, argVal: Set[(String, Set[String])]) {
     println("mp: "+ mp) 
     mp match {
         case "android/content/Intent/getAction" => {
               //println("getAction")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getAction")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
         }
         // This is the thing the fuzzer needs! 
         case "android/content/Intent/getExtras" => {
             //println("getExtras")
           state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> Map("getExtras"->Set())) 
         }
          case "android/content/Intent/getStringExtra" => {
                 //println("getStringExtra")
            val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getStringExtra")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
           //Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getStringExtra"->Set())) 
         }
         
         case "android/content/Intent/getBooleanArrayExtra" => {
                // println("getBooleanArrayExtra")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getBooleanArrayExtra")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
           //Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getBooleanArrayExtra"->Set())) 
         }
         
         case "android/content/Intent/getBooleanExtra" => {
             //println("getBooleanExtra")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getBooleanExtra")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
          // Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getBooleanArrayExtra"->Set())) 
         }
         
         case "android/content/Intent/getByteArrayExtra" => {
           //  println("getByteArrayExtra")
             val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getByteArrayExtra")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
           //Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getByteArrayExtra"->Set())) 
         }
         
         case "android/content/Intent/getByteExtra" => {
           // println("getByteExtra")
            val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getByteExtra")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
           //Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getByteExtra"->Set())) 
         }
         
         case "android/content/Intent/getCharArrayExtra" => {
            //println("getCharArrayExtra")
         val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getCharArrayExtra")
              state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
          // Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getCharArrayExtra"->Set())) 
         }
         
         case "android/content/Intent/getCharExtra" => {
             // println("getCharExtra")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getCharArrayExtra")
             Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> res)
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> Map("getCharExtra"->Set())) 
         }
         
         case "android/content/Intent/getCharSequenceArrayExtra" => {
            //   println("getCharSequenceArrayExtra")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getCharSequenceArrayExtra")
              state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
          // Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getCharSequenceArrayExtra"->Set())) 
         }
         
         case "android/content/Intent/getCharSequenceArrayListExtra" => {
              //println("getCharSequenceArrayListExtra")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getCharSequenceArrayListExtra")
             state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
         //  Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getCharSequenceArrayListExtra"->Set())) 
         }
          case "android/content/Intent/getCharSequenceExtra" => {
             //println("getCharSequenceExtra")
            val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getCharSequenceExtra")
              state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
           //Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getCharSequenceExtra"->Set())) 
         }
            case "android/content/Intent/getDoubleArrayExtra" => {
              // println("getDoubleArrayExtra")
              val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getDoubleArrayExtra")
             state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)}
             case "android/content/Intent/getIntArrayListExtra" => {
                  //println("getIntArrayListExtra")
               val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getIntArrayListExtra")
              state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
              }
              
         case "android/content/Intent/getDoubleExtra" => {
           // println("getDoubleExtra")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getDoubleExtra")
             state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
          // Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getDoubleExtra"->Set())) 
         }
         
          case "android/content/Intent/getIntExtra" => {
             // println("getIntExtra")
            val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getIntExtra")
             state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
          // Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getIntExtra"->Set())) 
         }
          
        
           case "android/content/Intent/getFloatArrayExtra" => {
              //println("getFloatArrayExtra")
             val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getFloatArrayExtra")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
         //  Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getFloatArrayExtra"->Set())) 
         }
         
         case "android/content/Intent/getFloatExtra" => {
             // println("getFloatExtra")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getFloatExtra")
             state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
         //  Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getFloatExtra"->Set())) 
         }
         case "android/content/Intent/getPackage" => {
             // println("getData")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getPackage")
             state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
         //  Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getPackage"->Set()))
           
         }
         case "android/content/Intent/getData" => {
           //println("getData")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getData")
            state.intentRelatedInfo += ((clsPath, enclosingMethPath, stmt) -> res)
          // Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getData"->Set()))
         }
         
         case "android/content/Intent/getType" => {
          //   println("getType")
           val res = 
             ForIntentFuzzerUtil.argTVsToMap(argVal, "getType")
            state.intentRelatedInfo+= ((clsPath, enclosingMethPath, stmt) -> res)
        //   Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap += ((clsPath, enclosingMethPath, stmt) -> Map("getType"->Set()))
         }
         
         //for getExtras, getBundleExtra usually don't use the several APIs?
         case "android/os/Bundle/getInt" | 
         "android/os/Bundle/getIntArray" | 
          "android/os/Bundle/getIntArrayList" | 
      "android/os/Bundle/get"| 
      "android/os/Bundle/getBoolean" | 
      "android/os/Bundle/getBooleanArray" | 
      "android/os/Bundle/getBundle"| 
      "android/os/Bundle/getByte"|
      "android/os/Bundle/getByteArray" |  
      "android/os/Bundle/getChar"| 
      "android/os/Bundle/getCharArray" | 
      "android/os/Bundle/getCharSequence" | 
      "android/os/Bundle/getCharSequenceArray" | 
       "android/os/Bundle/getCharSequenceArrayList" | 
       // getClassLoader
        "android/os/Bundle/getShort" |
      "android/os/Bundle/getShortArray" |
      "android/os/Bundle/getDouble" |
      "android/os/Bundle/getDoubleArray" |
      "android/os/Bundle/getFloat" | 
       "android/os/Bundle/getFloatArray" | 
      "android/os/Bundle/getLong" |
       "android/os/Bundle/getLongArray" |
      "android/os/Bundle/getShort"| 
      "android/os/Bundle/getString" | 
      "android/os/Bundle/getParcelable" |
      "android/os/Bundle/getParcelableArray" |
       "android/os/Bundle/getParcelableArrayList" |
      "android/os/Bundle/getSerializable" | 
      "android/os/Bundle/getSparseParcelableArray" | 
      "android/os/Bundle/getString" |
      "android/os/Bundle/getStringArray" | 
      "android/os/Bundle/getStringArrayList"
      => {
         //println("BUndles")
    	  var extraKeyValueTypeMap = Thread.currentThread().asInstanceOf[AnalysisHelperThread].receivingIntentProcessingMap.getOrElse((clsPath, enclosingMethPath, stmt), Map("getExtras" -> Set()))
    	  var keyVaues = extraKeyValueTypeMap.getOrElse("getExtras", Set.empty[IntentExtraKeyTypeAndValue]).asInstanceOf[Set[IntentExtraKeyTypeAndValue]]
    	  val extraKeyValueTypeMap2 = Map("getExtras" -> (keyVaues + new IntentExtraKeyTypeAndValue(mp, argVal.toString)))
    	  //=  keyVaues + new IntentExtraKeyValueAndType(mp, argVal.toString)
    	  state.intentRelatedInfo += 
    	    ((clsPath, enclosingMethPath, stmt) -> extraKeyValueTypeMap2)
       }
      
         case _ => {
          // println("set pastate after NOne : ",state.intentRelatedInfo )
         } 
     }
     // println("set pastate after : ",state.intentRelatedInfo )
     }
 
}