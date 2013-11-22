package org.ucombinator.dalvik.specialAPIs

import org.ucombinator.dalvik.cfa.cesk.CESKMachinary
import org.ucombinator.dalvik.cfa.cesk.StateSpace
import org.ucombinator.dalvik.syntax.Stmt
import org.ucombinator.dalvik.syntax.AExp
import org.ucombinator.utils.Debug
import org.ucombinator.dalvik.syntax.StForEqual

trait ReflectionAI extends StateSpace with CESKMachinary{

  import org.ucombinator.domains.CommonAbstractDomains._
  
  /**
   * This will get the field "name" , which is a string object value, and extend
   * that to the (fp, "ret") 
   * 
   * Note that all the string objects are join together
   * */
  
  def handleClassGetName(invokS: Stmt, argRegExps: List[AExp], objVals: D, ls: Stmt, s: Store, pst: PropertyStore, realN: Stmt, fp: FramePointer, kptr: KAddr, t: Time, tp: Time, k: Kont): Set[Conf] = {
    val classObjVals = filterClassObjVals(objVals, s)
    Debug.prntDebugInfo("the filtered class object is.length " + invokS.toString() , classObjVals.toList.length)
    
    val resStrValues = classObjVals.toList.foldLeft(s.mkDomainD())((res, classObjValv) => {
      val classObjVal = classObjValv.asInstanceOf[ObjectValue]
      val op = classObjVal.op
      val nameAddr = op.offset("name")
      val strVals = storeLookup(s, nameAddr)
      res join strVals
    })
    
    val newStore = storeUpdate(s, List((fp.offset("ret"), resStrValues)))
    Set(((PartialState(StForEqual(realN, realN.next,realN.clsPath, realN.methPath, realN.lineNumber), fp, newStore, pst, kptr, tp), k)))
  }
  
  /**
   * this will generate a class object on the heap, with the name updated to string reference of the classname
   * the class object value will be placed on the ret 
   */
  def handleClassForName(invokS: Stmt, argRegExps: List[AExp], objVals: D, ls: Stmt, s: Store, pst: PropertyStore, realN: Stmt, fp: FramePointer, kptr: KAddr, t: Time, tp: Time, k: Kont): Set[Conf] = {
     val classObjVals = filterClassObjVals(objVals, s)
     Debug.prntDebugInfo("the filtered class object is.length " + invokS.toString() , classObjVals.toList.length)
    
     // TODO: forName(String name, boolean initialize, ClassLoader loader)
     if(argRegExps.length != 1) {
       Set(((PartialState(StForEqual(realN, realN.next,realN.clsPath, realN.methPath, realN.lineNumber), fp, s, pst, kptr, tp), k)))
     }else {
       val argVals = atomEval( argRegExps.head, fp, s)
       val strVals = filterStrObjVals(filterObjValues(argVals, s), s)
       
        val objPointerCls = ObjectPointer(t, "java/lang/Class", ls)
        	val objValCls = ObjectValue(objPointerCls, "java/lang/Class")
        val clsFieldAddr = objPointerCls.offset("name")
        /**
         * damn it, there is another invariant problem. 
         * I'm just do crude conversion here, 'cos it is safe
         */
        val newStore = storeUpdate(s, List((clsFieldAddr, s.mkDomainD(strVals.toList.map(_.asInstanceOf[Value]): _*)), (fp.offset("ret"), s.mkDomainD(objValCls))))
          Set(((PartialState(StForEqual(realN, realN.next,realN.clsPath, realN.methPath, realN.lineNumber), fp, newStore, pst, kptr, tp), k)))
     }
  }
  
}