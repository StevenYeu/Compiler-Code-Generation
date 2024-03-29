//---------------------------------------------------------------------
// CSE 131 Reduced-C Compiler Project
// Copyright (C) 2008-2015 Garo Bournoutian and Rick Ord
// University of California, San Diego
//---------------------------------------------------------------------

import java_cup.runtime.*;
import java.util.Vector;
import java.util.List;
import java.util.HashMap;
import java.util.Stack;
class MyParser extends parser
{
	private Lexer m_lexer;
	private ErrorPrinter m_errors;
	private boolean m_debugMode;
	private int m_nNumErrors;
	private String m_strLastLexeme;
	private boolean m_bSyntaxError = true;
	private int m_nSavedLineNum;
    private boolean paramAmp = false;
    private int isInLoop = 0;
    private boolean isInStruct = false; // if inside structdef decl
    private boolean isMultiError = false; 
    private Scope scope;
    private String StructName;
    private String name;
    private boolean isThis = false;
    private boolean inThisFlag = false;
    private boolean structFuncCall = false; // if function called belongs to a struct
    private STO callingStruct; // used for funcall woth dot operator outisde of struct
    private boolean newCall = false; // new statement
    private AssemblyCodeGenerator codegen; 
    private int andor = 0;
    private HashMap<String,Integer> statMap = new HashMap<String,Integer>();
    // keep track of the offset
    private int offsetCnt = 0;
    // keep track of the offset in struct
    private int structOffset = 0;
    // keep track of the line num
    private int m_nSavedLineCnt;
    // for antifun - overload case, need func name and params info
    private STO someFunc;
    // for new case in doctor 
    private STO newSto;
    // for new case in doctor
    private boolean inNewIsPointer = false;
    // for codeblock in for each stmt
    private boolean isInForEach = false;
    private Stack<Boolean> ForEachStack = new Stack<Boolean>();
	private SymbolTable m_symtab;
	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public MyParser(Lexer lexer, ErrorPrinter errors, boolean debugMode)
	{
		m_lexer = lexer;
		m_symtab = new SymbolTable();
        codegen = new AssemblyCodeGenerator("rc.s");
		m_errors = errors;
		m_debugMode = debugMode;
		m_nNumErrors = 0;
	}
    // ---------------------
    //
    // ---------------------
    public void setSomeFunc(STO s){
        someFunc = s;
    }
    // ---------------------
    //
    // ---------------------
    public STO getSomeFunc(){
        return someFunc;

    }

    // ---------------------
    // 
    // ---------------------
    public void setNewSto(STO s){
        newSto = s;
    }
    // ---------------------
    //
    // ---------------------
    public STO getNewSto(){
        return newSto;
    }
    // ---------------------
    //
    // ---------------------
    public void setInNew(boolean b){
        inNewIsPointer = b;
    }
    // ---------------------
    //
    // ---------------------
    public boolean getInNew(){
        return inNewIsPointer;
    }

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public boolean Ok()
	{
		return m_nNumErrors == 0;
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public Symbol scan()
	{
		Token t = m_lexer.GetToken();

		//	We'll save the last token read for error messages.
		//	Sometimes, the token is lost reading for the next
		//	token which can be null.
		m_strLastLexeme = t.GetLexeme();

		switch (t.GetCode())
		{
			case sym.T_ID:
			case sym.T_ID_U:
			case sym.T_STR_LITERAL:
			case sym.T_FLOAT_LITERAL:
			case sym.T_INT_LITERAL:
				return new Symbol(t.GetCode(), t.GetLexeme());
			default:
				return new Symbol(t.GetCode());
		}
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public void syntax_error(Symbol s)
	{
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public void report_fatal_error(Symbol s)
	{
		m_nNumErrors++;
		if (m_bSyntaxError)
		{
			m_nNumErrors++;

			//	It is possible that the error was detected
			//	at the end of a line - in which case, s will
			//	be null.  Instead, we saved the last token
			//	read in to give a more meaningful error 
			//	message.
			m_errors.print(Formatter.toString(ErrorMsg.syntax_error, m_strLastLexeme));
		}
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public void unrecovered_syntax_error(Symbol s)
	{
		report_fatal_error(s);
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public void DisableSyntaxError()
	{
		m_bSyntaxError = false;
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public void EnableSyntaxError()
	{
		m_bSyntaxError = true;
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public String GetFile()
	{
		return m_lexer.getEPFilename();
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public int GetLineNum()
	{
		return m_lexer.getLineNumber();
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public void SaveLineNum()
	{
		m_nSavedLineNum = m_lexer.getLineNumber();
	}

    //----------------------------------------------------------------
    // This is used for hold off
    //----------------------------------------------------------------
    public void SaveLineCnt()
    {
		m_nSavedLineCnt = m_lexer.getLineNumber();
    }
	//----------------------------------------------------------------
	// This is also used for hold off
	//----------------------------------------------------------------
	public int GetSavedLineCnt()
	{
		return m_nSavedLineCnt;
	}



	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	public int GetSavedLineNum()
	{
		return m_nSavedLineNum;
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoProgramStart()
	{
		// Opens the global scope.
		m_symtab.openScope();
        codegen.formatHeader();
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoProgramEnd()
    {
        
        // empty the buffer at the end of the file so nothing gets stucked
        if(codegen.getholdOff()){
            codegen.TimeToWrite();
            codegen.setholdOff(false);
        }
        codegen.dispose();
		m_symtab.closeScope();
	}

	//----------------------------------------------------------------
	// for auto
	//----------------------------------------------------------------
	void DoVarDecl(String optstatic, String id, STO expr)
	{
        Type t = expr.getType();

		if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
		}

		VarSTO sto = new VarSTO(id,t);
        if(t instanceof ArrayType){
            sto.setIsAddressable(true);
            sto.setIsModifiable(false);
        }
        else{
            sto.setIsAddressable(false);
            sto.setIsModifiable(false);
        }
        //Assembly Writing: for init global/static var decl
        //should be the same as init for general global/static init
        if(m_symtab.getLevel() == 1){
            // set base and offset
            sto.setBase("%g0");
            sto.setOffset(id);

            // const init
            if(expr instanceof ConstSTO){
                ConstSTO exp = (ConstSTO)expr;
                int i;
                if(t instanceof BoolType){
                    i = exp.getBoolValue() ? 1 : 0;
                    String str = String.valueOf(i);
                    String name = id;
                    codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
                }
                else if(t instanceof IntType){
                    i = exp.getIntValue();
                    String str = String.valueOf(i);
                    String name = id;
                    codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
                }
                else if(t instanceof FloatType){
                    float f = exp.getFloatValue();
                    String str = String.valueOf(f);
                    String name = id;
                    codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
                }
            }
            //var init
            else{
                codegen.setholdOff(false);

                codegen.DoGlobalVarInitVar(sto, optstatic);

                codegen.TimeToWrite();

                codegen.DoVarAssign(sto, expr, null);

                //a sto for this init func, does nothing except holds offset and base
                STO func = new FuncSTO("tempFunc");
                int val = offsetCnt*4;
                func.setOffset("+"+String.valueOf(val));
                func.setBase("92");

                // init func ender
                codegen.initGlobalVarEnd(sto, func);
                offsetCnt = 0;     //reset counter after each init 



            }
        }
        // local init
        else{
            //set base and offset
            if(optstatic != null){
                sto.setBase("%g0");
                String name = m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id;
                if(statMap.containsKey(name)){
                    int val = statMap.get(name);
                    val++;
                    statMap.put(name,val);
                    sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                }
                else{
                    this.statMap.put(name,0);
                    sto.setOffset(name);
                }
            }
            else{
                sto.setBase("%fp");
                offsetCnt++;
                int val = -offsetCnt * t.getSize();
                String value = String.valueOf(val);
                sto.setOffset(value);
            }

            // const init
            if(expr instanceof ConstSTO){
                ConstSTO exp = (ConstSTO)expr;
                Type typ = exp.getType();
                int i;

                if(typ instanceof FloatType){
                    //static case
                    if(optstatic != null){
                        float f = exp.getFloatValue();
                        codegen.DoGlobalVarInitLit(sto, String.valueOf(f), sto.getOffset(), optstatic);
                    }
                    // nonstatic case
                    else{
                        codegen.DoFloatAssign(sto, expr, null);
                    }
                }
                else if(typ instanceof IntType){
                    i = exp.getIntValue();
                    String str = String.valueOf(i);
                    // static case
                    if(optstatic != null){
                        codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic);
                    }
                    // nonstatic case
                    else{
                        codegen.DoConstAssign(sto, str, exp.getName());
                    }
                }
                else if(typ instanceof BoolType){
                    i = exp.getBoolValue() ? 1 : 0;
                    String str = String.valueOf(i);
                    // static case
                    if(optstatic != null){
                        codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic);
                    }
                    // nonstatic case
                    else{
                        codegen.DoConstAssign(sto, str, exp.getName());
                    }

                }
                // to be implement array, struct etc

            }
            //var init
            else{
                if(optstatic != null){
                    codegen.setholdOff(false);

                    codegen.DoGlobalVarDecl(sto, sto.getOffset() ,optstatic);
                    codegen.DoStaticGuardStart(sto, ".$.init."+sto.getOffset());

                    codegen.TimeToWrite();
                }

                codegen.DoVarAssign(sto, expr, null);

                if(optstatic != null){
                    codegen.DoStaticGuardEnd(".$.init."+sto.getOffset());
                }
                

            }


        }

  		m_symtab.insert(sto);
	}



    //-------------------------------------------------------------------
    // Get new Call Flag
    //
    //-------------------------------------------------------------------
    public boolean getNewCall() {
      return newCall;
    }


    //-------------------------------------------------------------------
    // Set new Call Flag
    //
    //-------------------------------------------------------------------
    public void setNewCall(boolean b) {
      newCall = b;
    }




    //-------------------------------------------------------------------
    // Prints list of STOs
    //
    //-------------------------------------------------------------------
    public void printList(Vector<STO> l) {
       
        for(int i = 0; i < l.size(); i++) {
           System.out.println(l.get(i).getName());
        }
    }

    //--------------------------------------------------------------------
    // 
    //
    //--------------------------------------------------------------------
    public Vector<STO> OverloadCheck(String funcName, Vector<STO> funcs) {

        Vector<STO> overload = new Vector<STO>();
        for (int i =0; i < funcs.size(); i++) {
            if(funcs.get(i) instanceof FuncSTO) {
               if (funcName.equals(funcs.elementAt(i).getName())) {
                overload.add(funcs.elementAt(i));
               }         
            }

        }
        return overload;
    }

    public Vector<STO> addMembers(Vector<STO> funcs) {

        Vector<STO> mem = new Vector<STO>();
        for (int i =0; i < funcs.size(); i++) {
            if(funcs.get(i) instanceof VarSTO) {
                mem.add(funcs.elementAt(i));
            }

        }
        return mem;
    }

    public Vector<STO> addFuncs(Vector<STO> fun) {

        Vector<STO> funcs = new Vector<STO>();
        for (int i =0; i < funcs.size(); i++) {
            if(fun.get(i) instanceof FuncSTO) {
                funcs.add(fun.elementAt(i));
            }

        }
        return funcs;
    }


    public boolean getStructFunCall() {
    	return structFuncCall;
    }

    public void setStructFunCall(boolean b) {
    	structFuncCall = b;
    }




    //-----------------------------------------------------------------
    // For Structs Constructor
    // ----------------------------------------------------------------
	void DoCtorStructs(String id, Type t,Vector<STO> arraylist ,Vector<STO> params, String optstatic)
	{
        // changed 11/20
    /*Type t;
    if(wantedtype instanceof PointerType){
        t = ((PointerType)wantedtype).getNext();
    }
    else{
        t = wantedtype;
    }*/
    
    STO newCall = new VarSTO("new",t);// temp for new call 
    if(t instanceof ErrorType){
        return;
    }

  	if (m_symtab.accessLocal(id) != null)
    {
       if(this.getNewCall()) { // check if new call
           newCall = m_symtab.accessLocal(id);
       }
       else { // if not new call
          m_nNumErrors++;
          m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
          m_symtab.insert(new ErrorSTO(id)); // not sure if correct
          return;
       }
      
    }
    Type arr = new ArrayType("temp",0,0);


    if(!arraylist.isEmpty()) {
      arr =this.CreateArrayType(t,arraylist);
      if (arr instanceof ErrorType) {
        this.setNewCall(false);
        return;
      }
    }


    Type typ;
     STO fun; // var for current func
     Vector<STO> funPar; // vector for current funcs params
     STO curPar; // calling param being check
     STO funsCurPar; // cun func para in table being checked against 
     Type curType; // the type of calling param
     Type funsCurType; // the type of the func param
     if(isInStruct) {
       if(t.getName().equals(m_symtab.getStruct().getName())) {
          typ = m_symtab.getStruct().getType();
       }
       else {
        typ = t;
       }
     }
     else {
      typ = t;
     }

     STO result = new VarSTO(t.getName(),typ); // struct var thats goes int the table
     STO sto = new VarSTO(t.getName(),typ); // temp
     
     Vector<STO> overloaded = ((StructType)typ).OverloadCheckStructCall(typ.getName()); // of constructors

		 if (overloaded.size() == 1) { // non overload case

          int overParSize = ((FuncSTO)overloaded.get(0)).getParams().size();
          int parSize = params.size();
          fun = overloaded.get(0); // get the function from the table
          if(overParSize != parSize) { // if have different number of params print error
             m_nNumErrors++;
	           m_errors.print(Formatter.toString(ErrorMsg.error5n_Call,parSize,overParSize));
             this.setNewCall(false);
	           return;
          }
          else {
              if(overParSize == 0 && parSize == 0) { // case if calling function has no params
                    if(this.getNewCall() ) {
                      result = newCall;
                      //this.setNewCall(false);

                    }

                    // Array Case
                    if(!arraylist.isEmpty()) {
                       result = new VarSTO(id,arr);
                       // global case
                       if(m_symtab.getLevel() == 1){
                          result.setOffset(id);
                          result.setBase("%g0");
                          codegen.DoGlobalVarInitVar(result, optstatic);
                          if(codegen.getholdOff()){
                            codegen.TimeToWrite();
                          }
                          codegen.setholdOff(false);    
                        }
                        else{

                          if(optstatic != null){
                              // static local case
                              result.setOffset(m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id);
                              result.setBase("%g0");
                              String name = result.getOffset();
                              codegen.DoGlobalVarDecl(result, name, optstatic);
                              codegen.DoStaticGuardStart(result, ".$.init."+name);
                          }
                          else{
                              // regular local case
                              offsetCnt = offsetCnt + ((ArrayType)result.getType()).getTotalSize()/4;
                              result.setOffset(String.valueOf(offsetCnt * -4));
                              result.setBase("%fp");
                          }
                        }
                        result.setStructName(fun.getStructName());
                        result.setAssemblyName(((FuncSTO)fun).getAssemblyName());

                       // loop to create array of struct
                        for(int i = 0; i < ((ArrayType)arr).getSize(); i++) {

                          // -- changed
                          STO offset = new VarSTO(result.getName(), result.getType());
                          
                          offset.setOffset(String.valueOf(++offsetCnt * -4));
                          offset.setBase("%fp");
                          offset.setArrayTag(true);
                          codegen.DoStructArray(result,offset,i);
                          codegen.DoCtor(offset, fun);
                          // dto stuff
                          if(!this.getNewCall()) {
                            STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                            dtor = new FuncSTO(dtor.getName(),dtor.getType());
                            if(m_symtab.getLevel() == 1 || optstatic != null) {
                              dtor.setIsGlobal(true);
                            }
                            codegen.DoDtorHeader(dtor,offset);
                          }
                          

                        }
                        if(m_symtab.getLevel() == 1){

                          //a sto for this init func, does nothing except holds offset and base
                          STO func = new FuncSTO("tempFunc");
                          int val = offsetCnt*4;
                          func.setOffset("+"+String.valueOf(val));
                          func.setBase("92");

                          codegen.initGlobalVarEnd(result, func);
                          offsetCnt = 0;     //reset counter after each init -- 11/19 

                        }
                        else if(optstatic != null){
                            codegen.DoStaticGuardEnd(".$.init."+result.getOffset());

                        }
                    }
                    else {  // regular no param case
                       result = new VarSTO(id,typ);
                       result.setIsPointer(this.getInNew());
                       this.setInNew(false);
                         //Assembly Write: structcall
                      if(m_symtab.getLevel() == 1){
                          result.setOffset(id);
                          result.setBase("%g0");
                          codegen.DoGlobalVarInitVar(result, optstatic);
                          if(codegen.getholdOff()){
                              codegen.TimeToWrite();
                          }
                          codegen.setholdOff(false);
                    
                      }
                      // new case
                      else if(result.getIsPointer()){
                          result.setOffset(String.valueOf(offsetCnt * -4));
                          result.setBase("%fp");
                      }
                      else{
                          if(optstatic != null){
                              // static local case
                              result.setOffset(m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id);
                              result.setBase("%g0");
                              String name = result.getOffset();
                              codegen.DoGlobalVarDecl(result, name, optstatic);
                              codegen.DoStaticGuardStart(result, ".$.init."+name);
                          }
                          // regular local case
                          else{

                              offsetCnt = offsetCnt + result.getType().getSize()/4;
                              result.setOffset(String.valueOf(offsetCnt * -4));
                              result.setBase("%fp");
                          }
                      }
                      result.setStructName(fun.getStructName());
                      result.setAssemblyName(((FuncSTO)fun).getAssemblyName());

                      codegen.DoCtor(result, fun);
                      // dtor stuff regular for no param case
                      
                          if(!this.getNewCall()) {
                            STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                            dtor = new FuncSTO(dtor.getName(),dtor.getType());
                            if(m_symtab.getLevel() == 1 || optstatic != null) {
                              dtor.setIsGlobal(true);
                            }
                            codegen.DoDtorHeader(dtor,result);
                          }



                      if(m_symtab.getLevel() == 1){

                          //a sto for this init func, does nothing except holds offset and base
                          STO func = new FuncSTO("tempFunc");
                          int val = offsetCnt*4;
                          func.setOffset("+"+String.valueOf(val));
                          func.setBase("92");

                          codegen.initGlobalVarEnd(result, func);
                          offsetCnt = 0;     //reset counter after each init -- 11/19 

                      }
                      else if(optstatic != null){
                          codegen.DoStaticGuardEnd(".$.init."+result.getOffset());

                      }


                    }
                    result.setIsModifiable(true);
                    result.setIsAddressable(true);
                  
                    
                    m_symtab.insert(result);

                    if(this.getNewCall()) { this.setNewCall(false);}
                    return;

              }
              else { // case if nonzero params 
                 //codegen.DoThisParam(); 
                 result = this.DoFunctionCall(sto,params,overloaded);
                  //if(result instanceof ErrorSTO) {return;}
                 if(this.getNewCall() ) {
                   result = newCall;
                   this.setNewCall(false);

                 }
                 // array case
                 if(!arraylist.isEmpty()) {
                    result = new VarSTO(id,arr);
                    if(m_symtab.getLevel() == 1){
                       result.setOffset(id);
                       result.setBase("%g0");
                       codegen.DoGlobalVarInitVar(result, optstatic);
                       if(codegen.getholdOff()){
                         codegen.TimeToWrite();
                       }
                       codegen.setholdOff(false);    
                     }
                     else{
                       if(optstatic != null){
                         // static local case
                         result.setOffset(m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id);
                         result.setBase("%g0");
                         String name = result.getOffset();
                         codegen.DoGlobalVarDecl(result, name, optstatic);
                         codegen.DoStaticGuardStart(result, ".$.init."+name);
                       }
                       // regular local case 
                       else{
                         offsetCnt = offsetCnt + ((ArrayType)result.getType()).getTotalSize()/4;
                         result.setOffset(String.valueOf(offsetCnt * -4));
                         result.setBase("%fp");
                       }
                     }
                     result.setStructName(fun.getStructName());
                     result.setAssemblyName(((FuncSTO)fun).getAssemblyName());

                    // loop to create array of struct
                     for(int i = 0; i < ((ArrayType)arr).getSize(); i++) {
                       
                       // -- changed
                       STO offset = new VarSTO(result.getName(), result.getType());
                       offsetCnt++;
                       offset.setOffset(String.valueOf(offsetCnt * -4));
                       offset.setBase("%fp");
                       offset.setArrayTag(true);
                       codegen.DoStructArray(result,offset,i);
                       // -- changed
                       codegen.DoCtorThis(offset);
                       offsetCnt = codegen.DoFuncCallParam(offset, fun, params, offsetCnt);
                       // dtor stuff for param array case                       
                       if(!this.getNewCall()) {
                          STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                          dtor = new FuncSTO(dtor.getName(),dtor.getType());
                          if(m_symtab.getLevel() == 1 || optstatic != null) {
                            dtor.setIsGlobal(true);
                          }
                          codegen.DoDtorHeader(dtor,offset);
                       }

                     }
                     if(m_symtab.getLevel() == 1){

                       //a sto for this init func, does nothing except holds offset and base
                       STO func = new FuncSTO("tempFunc");
                       int val = offsetCnt*4;
                       func.setOffset("+"+String.valueOf(val));
                       func.setBase("92");

                       codegen.initGlobalVarEnd(result, func);
                       offsetCnt = 0;     //reset counter after each init -- 11/19 

                     }
                     else if(optstatic != null){
                          codegen.DoStaticGuardEnd(".$.init."+result.getOffset());

                     }



                 }
                 else { //regular nonzero param case
                    result = new VarSTO(id,typ);
                    result.setIsPointer(this.getInNew());
                    this.setInNew(false);
                    //Assembly Write: structcall
                  if(m_symtab.getLevel() == 1){
                     result.setOffset(id);
                     result.setBase("%g0");
                     codegen.DoGlobalVarInitVar(result, optstatic);
                     if(codegen.getholdOff()){
                         codegen.TimeToWrite();
                     }
                     codegen.setholdOff(false);
                  
                  }
                  // new case
                  else if(result.getIsPointer()){
                     result.setOffset(String.valueOf(offsetCnt * -4));
                     result.setBase("%fp");
                  }
                  else{
                      if(optstatic != null){
                         // static local case
                         result.setOffset(m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id);
                         result.setBase("%g0");
                         String name = result.getOffset();
                         codegen.DoGlobalVarDecl(result, name, optstatic);
                         codegen.DoStaticGuardStart(result, ".$.init."+name);
                       }
                       else{
                         // regular local case
                         offsetCnt = offsetCnt + result.getType().getSize()/4;
                         result.setOffset(String.valueOf(offsetCnt * -4));
                         result.setBase("%fp");
                       }
                  }
                  result.setAssemblyName(((FuncSTO)fun).getAssemblyName());
                  result.setStructName(fun.getStructName());
                  codegen.DoCtorThis(result);
                  offsetCnt = codegen.DoFuncCallParam(result, fun, params, offsetCnt);
                  
                  // dtor stuff for regular param case
                 if(!this.getNewCall()) {
                    STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                    dtor = new FuncSTO(dtor.getName(),dtor.getType());
                    if(m_symtab.getLevel() == 1 || optstatic != null) {
                        dtor.setIsGlobal(true);
                    }

                    codegen.DoDtorHeader(dtor,result);

                 }
               
                  if(m_symtab.getLevel() == 1){
                      //a sto for this init func, does nothing except holds offset and base
                      STO func = new FuncSTO("tempFunc");
                      int val = offsetCnt*4;
                      func.setOffset("+"+String.valueOf(val));
                      func.setBase("92");

                      codegen.initGlobalVarEnd(result, func);
                      offsetCnt = 0;     //reset counter after each init -- 11/19 

                  }
                  else if(optstatic != null){
                      codegen.DoStaticGuardEnd(".$.init."+result.getOffset());

                  }


                 }
                 result.setIsAddressable(true);
                 result.setIsModifiable(true);
                 
                  
                 m_symtab.insert(result);

                 if(this.getNewCall()) { this.setNewCall(false);}
                 return;
              }
          }
       }
       else { // overloadcase
           
           result = this.DoOverloadCall(sto,params,overloaded);
           if(this.getNewCall() ) {
             result = newCall;
             this.setNewCall(false);

           }
           // array case
           if(!arraylist.isEmpty()) {
              result = new VarSTO(id,arr);

              if(m_symtab.getLevel() == 1){
                 result.setOffset(id);
                 result.setBase("%g0");
                 codegen.DoGlobalVarInitVar(result, optstatic);
                 if(codegen.getholdOff()){
                   codegen.TimeToWrite();
                 }
                 codegen.setholdOff(false);    
               }
               else{
                   if(optstatic != null){
                       // static local case
                       result.setOffset(m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id);
                       result.setBase("%g0");
                       String name = result.getOffset();
                       codegen.DoGlobalVarDecl(result, name, optstatic);
                       codegen.DoStaticGuardStart(result, ".$.init."+name);
                   }
                   else{
                       // regular local case
                       offsetCnt = offsetCnt + ((ArrayType)result.getType()).getTotalSize()/4;
                       result.setOffset(String.valueOf(offsetCnt * -4));
                       result.setBase("%fp");
                   }
               }
              // loop to create array of struct
               for(int i = 0; i < ((ArrayType)arr).getLength(); i++) {

                   // --changed
                 STO offset = new VarSTO(result.getName(), result.getType());
                 offsetCnt++;
                 offset.setOffset(String.valueOf(offsetCnt * -4));
                 offset.setBase("%fp");
                 offset.setArrayTag(true);
                 codegen.DoStructArray(result,offset,i);
                 if(params.isEmpty()){
                     STO antifun = this.getSomeFunc();
                     codegen.DoCtor(offset, antifun);
                     // dtor stuff for overload, array case no param
                     if(!this.getNewCall()) {
                        STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                        dtor = new FuncSTO(dtor.getName(),dtor.getType());
                        if(m_symtab.getLevel() == 1 || optstatic != null) {
                            dtor.setIsGlobal(true);
                        }
                        codegen.DoDtorHeader(dtor,offset);
                     }

                 }
                 else{
                     // -- changed
                     codegen.DoCtorThis(offset);
                     STO antifun = this.getSomeFunc();
                     offsetCnt = codegen.DoFuncCallParam(result, antifun, params, offsetCnt);
                     
                     // dtor stuff  overload case , array, params
                     
                     if(!this.getNewCall()) {
                        STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                        dtor = new FuncSTO(dtor.getName(),dtor.getType());
                        if(m_symtab.getLevel() == 1 || optstatic != null) {
                           dtor.setIsGlobal(true);
                        }

                        codegen.DoDtorHeader(dtor,result);
                     }


                 }


               }
               if(m_symtab.getLevel() == 1){

                 //a sto for this init func, does nothing except holds offset and base
                 STO func = new FuncSTO("tempFunc");
                 int val = offsetCnt*4;
                 func.setOffset("+"+String.valueOf(val));
                 func.setBase("92");

                 codegen.initGlobalVarEnd(result, func);
                 offsetCnt = 0;     //reset counter after each init -- 11/19 

               }
               else if(optstatic != null){
                 codegen.DoStaticGuardEnd(".$.init."+result.getOffset());

               }




           }
           // regular overload case
           else {

              result = new VarSTO(id,typ);
              result.setIsPointer(this.getInNew());
              this.setInNew(false);

              //Assembly Write: structcall
              if(m_symtab.getLevel() == 1){
                  result.setOffset(id);
                  result.setBase("%g0");
                  codegen.DoGlobalVarInitVar(result, optstatic);
                  if(codegen.getholdOff()){
                      codegen.TimeToWrite();
                  }
                  codegen.setholdOff(false);
                    
              }
              // new case
              else if(result.getIsPointer()){
                  result.setOffset(String.valueOf(offsetCnt * -4));
                  result.setBase("%fp");
              }
              else{
                   if(optstatic != null){
                       // static local case
                       result.setOffset(m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id);
                       result.setBase("%g0");
                       String name = result.getOffset();
                       codegen.DoGlobalVarDecl(result, name, optstatic);
                       codegen.DoStaticGuardStart(result, ".$.init."+name);
                   }
                   else{
                       // regular local case
                       offsetCnt = offsetCnt + result.getType().getSize()/4;
                       result.setOffset(String.valueOf(offsetCnt * -4));
                       result.setBase("%fp");
                   }
              }
               
              if(params.isEmpty()){
                  
                  result.setAssemblyName("void");
                  codegen.DoCtor(result, result);

                  // dtor stuff for reg overload no params
                  if(!this.getNewCall()) {
                     STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                     dtor = new FuncSTO(dtor.getName(),dtor.getType());
                     if(m_symtab.getLevel() == 1 || optstatic  != null) {
                        dtor.setIsGlobal(true);
                     }

                     codegen.DoDtorHeader(dtor,result);
                  }

              }
              else{

                  codegen.DoCtorThis(result);
                  STO antifun = this.getSomeFunc();
                  offsetCnt = codegen.DoFuncCallParam(result, antifun, params, offsetCnt);
                  
                  // dot stuff for reg overload params
                  if(!this.getNewCall()) {
                     STO dtor = ((StructType)typ).getDtor("~"+typ.getName());
                     dtor = new FuncSTO(dtor.getName(),dtor.getType());
                     if(m_symtab.getLevel() == 1 || optstatic  != null) {
                        dtor.setIsGlobal(true);
                     }

                     codegen.DoDtorHeader(dtor,result);
                  }

              }
              if(m_symtab.getLevel() == 1){

                  //a sto for this init func, does nothing except holds offset and base
                  STO func = new FuncSTO("tempFunc");
                  int val = offsetCnt*4;
                  func.setOffset("+"+String.valueOf(val));
                  func.setBase("92");

                  codegen.initGlobalVarEnd(result, func);
                  offsetCnt = 0;     //reset counter after each init -- 11/19 

              }
              else if(optstatic != null){
                  codegen.DoStaticGuardEnd(".$.init."+result.getOffset());

              }


           }
           

           result.setIsAddressable(true);
           result.setIsModifiable(true);
           m_symtab.insert(result);
           if(this.getNewCall()) { this.setNewCall(false);}
           return;
       }

	}




    // decl of var in struct
    void DoStructVarDecl(Type t, String id, Vector<STO> arraylist)
	{
		
        VarSTO sto;
        if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.error13a_Struct, id));
		}


        if(arraylist.size() > 0) {
            int numDim = arraylist.size();
            STO sizeStoTop = arraylist.elementAt(0);
            int len = ((ConstSTO)sizeStoTop).getIntValue();
            ArrayType aTopType = new ArrayType(t.getName()+"["+ ((ConstSTO)sizeStoTop).getIntValue() +"]", ((ConstSTO)sizeStoTop).getIntValue(), numDim),length;

            
            for(int i = 1; i <=numDim; i++){
            
                if(i == numDim){
                    aTopType.addNext(t);
                }
                else{  
                  STO sizeSto = arraylist.elementAt(i);
                  int l = ((ConstSTO)sizeSto).getIntValue();
                  ArrayType typ = new ArrayType(t.getName()+"["+ ((ConstSTO)sizeSto).getIntValue() +"]", ((ConstSTO)sizeSto).getIntValue(),numDim-i,l);
                  aTopType.addNext(typ);

                }                        
            }
            
		    sto = new VarSTO(id,aTopType);
            sto.setStructOffset(structOffset * 4);
            structOffset += ((ConstSTO)sizeStoTop).getIntValue();
            //sto.setArrayTag(true); 
            sto.setIsAddressable(true);
            sto.setIsModifiable(false);
        }
        else {
 		    sto = new VarSTO(id,t);
            // set offset in struct 
            sto.setStructOffset(4 * structOffset++);
            sto.setIsAddressable(true);
            sto.setIsModifiable(true);

        
        }
        sto.setStructTag(true);
        
        
        // set var in struct to mod-lval
		m_symtab.insert(sto);
        Scope var = m_symtab.getCurrScope();
        ((StructType)m_symtab.getStruct().getType()).setScope(var);
	}


    //----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoVarDecl2(String optstatic,String id, Type t, Vector<STO> arraylist, STO expr)
	{
        
        int numDim = arraylist.size();
        VarSTO sto;
        if( expr instanceof ErrorSTO) {
          return;

        }

        if (m_symtab.accessLocal(id) != null){
		    m_nNumErrors++;
		    m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
            return;
		}

        // var decl for array  
        if(numDim > 0)
        {
            for(int i = 0; i < numDim; i++){ // for arrays
                STO arrayDim = arraylist.elementAt(i);
                
                if(arrayDim instanceof ErrorSTO || arrayDim.getType() instanceof ErrorType) {
                   return;
                }

 
                if(! arrayDim.getType().isEquivalent(new IntType("int"))) {
                    m_nNumErrors++;
			        m_errors.print(Formatter.toString(ErrorMsg.error10i_Array, arrayDim.getType().getName()));
                    return;
                }
                else if(!(arrayDim instanceof ConstSTO)){
                    m_nNumErrors++;
                    m_errors.print(ErrorMsg.error10c_Array);
                    return;
                }
                else if(((ConstSTO)arrayDim).getIntValue() <=0){
                    m_nNumErrors++;
			        m_errors.print(Formatter.toString(ErrorMsg.error10z_Array, (((ConstSTO)arrayDim).getIntValue())));
                    return;
                }

            }

            STO sizeStoTop = arraylist.elementAt(0);
            String arr = this.CreateArray(arraylist);
            ArrayType aTopType = new ArrayType(t.getName() + arr, ((ConstSTO)sizeStoTop).getIntValue(), numDim,((ConstSTO)sizeStoTop).getIntValue());


            
            for(int i = 1; i <=numDim; i++){
            
                if(i == numDim){
                    aTopType.addNext(t);
                }
                else{  
                  STO sizeSto = arraylist.elementAt(i);
                  if((arr.indexOf("]")+1) != -1){
                     arr = arr.substring(arr.indexOf("]")+1);
                  }

                  ArrayType typ = new ArrayType(t.getName()+arr, ((ConstSTO)sizeSto).getIntValue(),numDim-i,((ConstSTO)sizeSto).getIntValue());
                  aTopType.addNext(typ);
                }
                        
            }
 

            aTopType.setLength(((ConstSTO)sizeStoTop).getIntValue());
            sto = new VarSTO(id, aTopType);

            // Assembly Write: array decl in Global scope    
            if(m_symtab.getLevel() == 1){
                String name = sto.getName();
                codegen.DoGlobalVarDecl(sto, name, optstatic);
                sto.setBase("%g0");
                sto.setOffset(id);

            }
            // local scope
            else{
                                            
                if(optstatic != null){
                    // static array uninit
                    String name = m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id;
                    sto.setBase("%g0");
                    if(statMap.containsKey(name)){
                        int val = statMap.get(name);
                        val++;
                        statMap.put(name,val);
                        sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                    }
                    else{
                        this.statMap.put(name,0);
                        sto.setOffset(name);
                    }

                    codegen.DoGlobalVarDecl(sto, sto.getOffset(), optstatic);
                }
                // non static array uninit
                else{
                    // set base and offset
                    offsetCnt = offsetCnt + ((ArrayType)sto.getType()).getTotalSize()/4;
                    sto.setBase("%fp");
                    sto.setOffset(String.valueOf(offsetCnt * -4));
                }
                
            }

            
            // non-mod l-val for array
            sto.setIsAddressable(true);
            sto.setIsModifiable(false);
        }
        else {
            // var decl for pointer 
            if(t instanceof PointerType){
                // uninit case
                if(expr == null) {
              	    sto = new VarSTO(id,t);
                    // global uninit case
                    if(m_symtab.getLevel() == 1){
                        sto.setBase("%g0");
                        sto.setOffset(id);

                        String name = sto.getName();
                        codegen.DoGlobalVarDecl(sto, name, optstatic);

                    }
                    // local case
                    else{

                        //local static case
                        if(optstatic != null){
                            String name = m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id;

                            sto.setBase("%g0");
                            if(statMap.containsKey(name)){
                                int val = statMap.get(name);
                                val++;
                                statMap.put(name,val);
                                sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                            }
                            else{
                                this.statMap.put(name,0);
                                sto.setOffset(name);
                            }

                            codegen.DoGlobalVarDecl(sto, sto.getOffset(), optstatic);
                            
                        }
                        else{
                            //local non static case
                            // set base and offset
                            sto.setBase("%fp");
                            sto.setOffset(String.valueOf(++offsetCnt * -4));

                        }
                        
                    }
		            m_symtab.insert(sto);
                    return;
                }
                
                else if(!(expr.getType() instanceof PointerType)){
                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error8_Assign, expr.getType().getName(),t.getName()));
                    return;
                }
                else if(!expr.getType().isAssignable(t)){
                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error8_Assign, expr.getType().getName(),t.getName()));
                    return;
                }
                
                sto = new VarSTO(id,t);
                //Global init case
                if(m_symtab.getLevel() == 1){
                    sto.setBase("%g0");
                    sto.setOffset(id);
                 
                    codegen.setholdOff(false);
                    
                    codegen.DoGlobalVarInitVar(sto, optstatic);
                    
                    codegen.TimeToWrite();

                    codegen.DoVarAssign(sto,expr, null);
                        
                    //a sto for this init func, does nothing except holds offset and base
                    STO func = new FuncSTO("tempFunc");
                    int val = offsetCnt*4;
                    func.setOffset("+"+String.valueOf(val));
                    func.setBase("92");

                    // init func ender
                    codegen.initGlobalVarEnd(sto, func);
                    offsetCnt = 0;     //reset counter after each init -- 11/19 
                }
                // local init case
                else{


                    // local static case
                    if(optstatic != null){
                        
                        STO currFunc = m_symtab.getFunc();
                        
                        String name = currFunc.getName()+"."+((FuncSTO)currFunc).getAssemblyName()+"."+sto.getName();
                        
                        // set base and offset
                        sto.setBase("%g0");
                        if(statMap.containsKey(name)){
                            int val = statMap.get(name);
                            val++;
                            statMap.put(name,val);
                            sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                        }
                        else{
                            this.statMap.put(name,0);
                            sto.setOffset(name);
                        }

                        codegen.setholdOff(false);

                        codegen.DoGlobalVarDecl(sto, sto.getOffset(), optstatic);
                        codegen.DoStaticGuardStart(sto, ".$.init."+sto.getOffset());

                        codegen.TimeToWrite();

                        codegen.DoVarAssign(sto, expr, null);

                        codegen.DoStaticGuardEnd(".$.init."+sto.getOffset());


                    }
                    // local nonstatic case
                    else{
                        //set base and offset
                        sto.setBase("%fp");
                        sto.setOffset(String.valueOf(++offsetCnt * -4));

                        codegen.DoVarAssign(sto, expr, null);
                    }
                }

                //lval for pointer
                sto.setIsAddressable(true);
                sto.setIsModifiable(true);
  
            }
            else{            

                if(expr == null) {
                    
              	    sto = new VarSTO(id,t);


                    //Assembly writing: for uninit global var decl
                    //global scope is always 1
                    if(m_symtab.getLevel() == 1){
                        sto.setBase("%g0");
                        sto.setOffset(id);
                        String name = id;
                        codegen.DoGlobalVarDecl(sto, name, optstatic);
                    }
                    // assembly for uninit local var decl
                    else{
                        
                        // static case
                        if(optstatic != null){
                    
                            String name = m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id;
                            sto.setBase("%g0");
                            if(statMap.containsKey(name)){
                                int val = statMap.get(name);
                                val++;
                                statMap.put(name,val);
                                sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                            }
                            else{
                                this.statMap.put(name,0);
                                sto.setOffset(name);
                            }

                        

                            codegen.DoGlobalVarDecl(sto, name, optstatic);
                        }
                        // nonstatic case
                        else{
                            sto.setBase("%fp");
                            sto.setOffset(String.valueOf(++offsetCnt * -4));

                        }
                        
                    }
		            m_symtab.insert(sto);
                    return;
                }
		   
                else if(!expr.getType().isAssignable(t)){
                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error8_Assign, expr.getType().getName(),t.getName()));
                    return;
                }

                sto = new VarSTO(id,t);


                //assembly for init global/static var decl
                Type typ = sto.getType();

                // case in global
                if(m_symtab.getLevel() == 1){

                    //set base and offset
                    sto.setBase("%g0");
                    sto.setOffset(id);

                    // const init
                    if(expr instanceof ConstSTO){
                        ConstSTO exp = (ConstSTO)expr;
                        int i;
                        if(typ instanceof BoolType){
                            i = exp.getBoolValue() ? 1 : 0;
                            String str = String.valueOf(i);
                            String name = id;
                            codegen.DoGlobalVarInitLit(sto, str, id, optstatic);
                        }
                        else if(typ instanceof IntType){
                            i = exp.getIntValue();
                            String str = String.valueOf(i);
                            String name = id;
                            codegen.DoGlobalVarInitLit(sto, str, id, optstatic);
                        }
                        else if(typ instanceof FloatType){

                            float f = exp.getFloatValue();
                            String str = String.valueOf(f);
                            String name = id;
                            codegen.DoGlobalVarInitLit(sto, str, id, optstatic);
                        }
                    } 
                    // var init
                    else{
                        codegen.setholdOff(false);

                        // init func header
                        codegen.DoGlobalVarInitVar(sto, optstatic);

                        // write when the hold off is off
                        codegen.TimeToWrite();

                        // Type promotion
                        if(sto.getType() instanceof FloatType && expr.getType() instanceof IntType){
                            // This does nothing except holds offset and base 
                            STO promote = new ExprSTO("temp");
                            offsetCnt++;
                            int value = -offsetCnt*4;
                            promote.setOffset(String.valueOf(value));
                            promote.setBase("%fp");

                            codegen.DoVarAssign(sto, expr, promote);
                  
                        }
                        else{
                            codegen.DoVarAssign(sto,expr, null);
                        }



                        //a sto for this init func, does nothing except holds offset and base
                        STO func = new FuncSTO("tempFunc");
                        int val = offsetCnt*4;
                        func.setOffset("+"+String.valueOf(val));
                        func.setBase("92");


                        // init func ender
                        codegen.initGlobalVarEnd(sto, func);
                        offsetCnt = 0;     //reset counter after each init -- 11/19 


                    }
                }
                // case in local
                else{

                    //set base and offset
                    if(optstatic == null){
                        sto.setBase("%fp");
                        sto.setOffset(String.valueOf(++offsetCnt * -4));
                    }
                    else{
                        sto.setBase("%g0");
                        String name = m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id;
                        if(statMap.containsKey(name)){
                            int val = statMap.get(name);
                            val++;
                            statMap.put(name,val);
                            sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                       }
                       else{
                           this.statMap.put(name,0);
                           sto.setOffset(name);
                       }

                    }

                    // const init
                    if(expr instanceof ConstSTO){
                        ConstSTO exp = (ConstSTO)expr;
                        
                        int i;

                        if(typ instanceof FloatType){
                            // static case
                            if(optstatic != null){
                                float f = exp.getFloatValue();
                            
                                codegen.DoGlobalVarInitLit(sto, String.valueOf(f), sto.getOffset(), optstatic);

                            }
                            else{
                                if(expr.getType() instanceof IntType){
                                    // do Type Promotion
                                    STO promote = new ExprSTO("temp");   
                                    offsetCnt ++;
                                    int exo = -offsetCnt * 4;
                                    promote.setBase("%fp");
                                    promote.setOffset(String.valueOf(exo));

                                    codegen.DoFloatAssign(sto, expr, promote);
                                
                                }
                                else{
                                    // float to float
                                    codegen.DoFloatAssign(sto, expr, null);
                                }
                            }
                        }
                        else if(typ instanceof IntType){
                            i = exp.getIntValue();
                            String str = String.valueOf(i);
                            // static case
                            if(optstatic != null){
  
                                codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic);
                            }
                            else{
                                codegen.DoConstAssign(sto, str, exp.getName());
                            }
                        }
                        else if(typ instanceof BoolType){
                            i = exp.getBoolValue() ? 1 : 0;
                            String str = String.valueOf(i);
                            // static case
                            if(optstatic != null){
                                 codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic); 
                            }
                            else{
                                codegen.DoConstAssign(sto, str, exp.getName());
                            }

                        }
                    
                    }
                    //var init
                    else{
                        // static case header
                        if(optstatic != null){
                            codegen.setholdOff(false);

                            String name = ".$.init."+sto.getOffset();

                            codegen.DoGlobalVarDecl(sto, sto.getOffset(), optstatic);
                            codegen.DoStaticGuardStart(sto, name);

                            codegen.TimeToWrite();

                        }
                         
                        if(sto.getType() instanceof FloatType && expr.getType() instanceof IntType){
                            // This does nothing except holds offset and base 
                            STO promote = new ExprSTO("temp");
                            offsetCnt++;
                            int pro = -offsetCnt*4;
                            promote.setOffset(String.valueOf(pro));
                            promote.setBase("%fp");

                            codegen.DoVarAssign(sto, expr, promote);
                  
                        }
                        else{
                            codegen.DoVarAssign(sto, expr, null);
                        }

                        // static case ender
                        if(optstatic != null){

                            codegen.DoStaticGuardEnd(".$.init."+sto.getOffset());
                        }
                        
                    }
                }
                
                sto.setIsAddressable(true);
                sto.setIsModifiable(true);
                
            }
        }
            
       
		m_symtab.insert(sto);
            
        
	}

    String CreateArray(Vector<STO> list) {
        String s = "";
        for(int i = 0; i < list.size(); i++){
            s =s.concat("[" + ((ConstSTO)list.get(i)).getIntValue() + "]");
        }
        return s;
        
    }

    //---------------------
    //
    //---------------------
    void setDoInForEach(boolean b){
        ForEachStack.push(b);
    }
    //---------------------
    //
    //---------------------
    boolean getDoInForEach(){
        if(ForEachStack.isEmpty()){
            return false;
        }
        return ForEachStack.peek();
    }

    //---------------------
    //
    //---------------------
    boolean getDoInForEachPop(){
        if(ForEachStack.isEmpty()){
            return false;
        }

        return ForEachStack.pop();
    }
    //----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoForEachDecl(Type iterType, Object opRef, String id, STO expr)
	{ 


        String s = opRef.toString();

		if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
            return;
		}
        VarSTO sto = new VarSTO(id,iterType);  

        if( s == "&"){
            sto.flag= true;
        }
        // Assembly Write: foreach stmt
        // set offset and base
        sto.setBase("%fp");
        sto.setOffset(String.valueOf(++offsetCnt * -4));

        ExprSTO theFuture = new ExprSTO("future");
        theFuture.setBase("%fp");
        theFuture.setOffset(String.valueOf(++offsetCnt * -4));
        codegen.DoForEach(expr, sto, s, theFuture);
      
		m_symtab.insert(sto);

        if (!(expr.getType() instanceof ArrayType)){
	        m_nNumErrors++;
			m_errors.print(ErrorMsg.error12a_Foreach);
            return;
        }

        if ( s == "&" ){

            //sto.setArrayTag(true);
            // set ref flag 
            sto.flag = true;

            if(!(((ArrayType)expr.getType()).getNext().isEquivalent(iterType))){

                
                m_nNumErrors++;
			    m_errors.print(Formatter.toString(ErrorMsg.error12r_Foreach, ( (ArrayType) expr.getType()).getNext().getName(), id, iterType.getName()));
                return;

            }

        }
        else{
            if(!(((ArrayType)expr.getType()).getNext().isAssignable(iterType))){
                
                m_nNumErrors++;
			    m_errors.print(Formatter.toString(ErrorMsg.error12v_Foreach, ( (ArrayType) expr.getType()).getNext().getName(), id, iterType.getName()));
                return;

            }
        }
		//VarSTO sto = new VarSTO(id,iterType);        
		//m_symtab.insert(sto);
	}

    public void CallDoForEachEnd(){
        codegen.DoWhileCloseLoop();
    }


	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoExternDecl(String id, Type t, Vector<STO> arraylist)
	{
        int numDim = arraylist.size();
        VarSTO sto;

		if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
		}

        // var decl for array  
        if(numDim > 0)
        {

            STO sizeStoTop = arraylist.elementAt(0);
            String arr = this.CreateArray(arraylist);
            ArrayType aTopType = new ArrayType(t.getName() + arr, ((ConstSTO)sizeStoTop).getIntValue(), numDim,((ConstSTO)sizeStoTop).getIntValue());

            sto = new VarSTO(id, aTopType);

            // Assembly Write: array decl in Global scope    
            if(m_symtab.getLevel() == 1){
                sto.setBase("%g0");
                sto.setOffset(id);
            }
            // local scope
            else{
                // set base and offset
                offsetCnt = offsetCnt + ((ArrayType)sto.getType()).getTotalSize()/4;
                sto.setBase("%fp");
                sto.setOffset(String.valueOf(offsetCnt * -4));
                
            }          
            // non-mod l-val for array
            sto.setIsAddressable(true);
            sto.setIsModifiable(false);
        }
        else {
            // var decl for pointer 
            if(t instanceof PointerType){
                                
                sto = new VarSTO(id,t);
                //Global init case
                if(m_symtab.getLevel() == 1){
                    sto.setBase("%g0");
                    sto.setOffset(id);
                 
                }
                // local init case
                else{
                    //set base and offset
                    sto.setBase("%fp");
                    sto.setOffset(String.valueOf(++offsetCnt * -4));
                }

                //lval for pointer
                sto.setIsAddressable(true);
                sto.setIsModifiable(true);
  
            }
            //var decl for regular case
            else{            

                sto = new VarSTO(id,t);

                // case in global
                if(m_symtab.getLevel() == 1){
                    //set base and offset
                    sto.setBase("%g0");
                    sto.setOffset(id);
                }
                // case in local
                else{
                    //set base and offset                    
                    sto.setBase("%fp");
                    sto.setOffset(String.valueOf(++offsetCnt * -4));

                }
                sto.setIsAddressable(true);
                sto.setIsModifiable(true);              
            }
        }
		m_symtab.insert(sto);
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoConstDecl2(String optstatic, String id, Type t, STO constexpr)
	{
        if(constexpr instanceof ErrorSTO){
            return;
        }

		if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
            return;
		}
       				
        if( !(constexpr instanceof ConstSTO)){
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error8_CompileTime, id));
            return;
            
        }
        if( !(constexpr.getType().isAssignable(t)))
        {
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error8_Assign, constexpr.getType().getName(),t.getName()));
            return;
        }
        else{
            ConstSTO sto;
            if (t instanceof IntType) {
                sto = new ConstSTO(id, t, ((ConstSTO)constexpr).getIntValue());   // fix me Done
            }
            else if (t instanceof FloatType) {
                sto = new ConstSTO(id, t, ((ConstSTO)constexpr).getFloatValue());   // fix me Done

            }
            else {
                if(((ConstSTO)constexpr).getBoolValue())
                    sto = new ConstSTO(id,t,1);
                else
                    sto = new ConstSTO(id,t,0);
            }
          

            sto.setIsModifiable(false);
            sto.setIsAddressable(true);
            
            //tag it here to make a var
            sto.setLitTag(true);

            //assembly for init global/static var decl
            if(m_symtab.getLevel() == 1){
                
                //set offset and base
                sto.setOffset(id);
                sto.setBase("%g0");
                    
                ConstSTO exp = (ConstSTO)constexpr;
                int i;
                if(t instanceof BoolType){
                    i = exp.getBoolValue() ? 1 : 0;
                    String str = String.valueOf(i);
                    String name = id;
                    codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
                }
                else if(t instanceof IntType){
                    i = exp.getIntValue();
                    String str = String.valueOf(i);
                    String name = id;
                    codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
                }
                else if(t instanceof FloatType){
                    float f = exp.getFloatValue();
                    String str = String.valueOf(f);
                    String name = id;
                    codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
                }
                    
            }
            else{

                // set base and offset
                if(optstatic != null){
                    sto.setBase("%g0");
                    String name = m_symtab.getFunc().getName() + "." + m_symtab.getFunc().getAssemblyName() + "." + id;
                    if(statMap.containsKey(name)){
                        int val = statMap.get(name);
                        val++;
                        statMap.put(name,val);
                        sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                    }
                    else{
                        this.statMap.put(name,0);
                        sto.setOffset(name);
                    }
                    
                }
                else{
                    sto.setBase("%fp");
                    
                    offsetCnt++;
                    int val = -offsetCnt * t.getSize();
                    String value = String.valueOf(val);
                    sto.setOffset(value);
                }
                    
                ConstSTO exp = (ConstSTO)constexpr;
                int i;

                if(t instanceof FloatType){
                    if(optstatic != null){
                        float f = exp.getFloatValue();
                        codegen.DoGlobalVarInitLit(sto, String.valueOf(f), sto.getOffset(), optstatic);
                    }
                    else{
                        // Type Promotion
                        if( constexpr.getType() instanceof IntType){
                            STO promote = new ExprSTO("temp");
                            offsetCnt ++;
                            int exo = -offsetCnt * 4;
                            promote.setBase("%fp");
                            promote.setOffset(String.valueOf(exo));
                     
                            if(exp.getLitTag()){
                                //treat non-lit const like var
                                codegen.DoVarAssign(sto, constexpr, promote);
                            }
                            else{
                                codegen.DoFloatAssign(sto,constexpr, promote);
                            }
                        }
                        else{
                            // float to float
                            if(exp.getLitTag()){
                                codegen.DoVarAssign(sto, constexpr, null);
                            }
                    
                            else{
                                codegen.DoFloatAssign(sto, constexpr, null);
                            }
                        }
                    }
                }
                else if(t instanceof IntType){
                    i = exp.getIntValue();
                    String str = String.valueOf(i);
                    if(optstatic != null){
                        codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic);
                    }
                    else{
                        codegen.DoConstAssign(sto, str, exp.getName());
                    }
                }
                else if(t instanceof BoolType){
                    i = exp.getBoolValue() ? 1 : 0;
                    String str = String.valueOf(i);
                    if(optstatic != null){
                        codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic);
                    }
                    else{
                        codegen.DoConstAssign(sto, str, exp.getName());
                    }

                }
                // to be implement array, struct -- dont need to do, doesn't make much sense 

            }


           	m_symtab.insert(sto);
        }
	}


    // auto for const decl
    void DoAutoDecl(String optstatic, String id, STO expr)
	{
        if(!(expr instanceof ConstSTO))
            return;

		if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
		}
       		
		ConstSTO sto;
        if (expr.getType() instanceof IntType) {
            sto = new ConstSTO(id, expr.getType(), ((ConstSTO)expr).getIntValue());   // fix me Done
        }
        else if (expr.getType() instanceof FloatType) {
            sto = new ConstSTO(id, expr.getType(), ((ConstSTO)expr).getFloatValue());   // fix me Done
        }
        else {
            if(((ConstSTO)expr).getBoolValue())
                sto = new ConstSTO(id,expr.getType(),1);
            else
                sto = new ConstSTO(id,expr.getType(),0);   
        }

        sto.setIsModifiable(false);
        sto.setIsAddressable(true);

        //tag it here to make a var
       sto.setLitTag(true);

       ConstSTO exp = (ConstSTO)expr;
       Type t = sto.getType();

       //assembly for init global/static var decl
       if(m_symtab.getLevel() == 1){
                
           //set offset and base
           sto.setOffset(id);
           sto.setBase("%g0");
                    
           int i;
           if(t instanceof BoolType){
               i = exp.getBoolValue() ? 1 : 0;
               String str = String.valueOf(i);
               String name = id;
               codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
           }
           else if(t instanceof IntType){
               i = exp.getIntValue();
               String str = String.valueOf(i);
               String name = id;
               codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
           }
           else if(t instanceof FloatType){
               float f = exp.getFloatValue();
               String str = String.valueOf(f);
               String name = id;
               codegen.DoGlobalVarInitLit(sto, str, name, optstatic);
           }
                    
       }
       else{
           if(optstatic != null){
               sto.setBase("%g0");
               String name = m_symtab.getFunc().getName()+"."+m_symtab.getFunc().getAssemblyName()+"."+id;
                if(statMap.containsKey(name)){
                    int val = statMap.get(name);
                    val++;
                    statMap.put(name,val);
                    sto.setOffset(name+"$"+String.valueOf(this.statMap.get(name)));
                }
                else{
                    this.statMap.put(name,0);
                    sto.setOffset(name);
                }
            
           }
           else{
               // set base and offset
               sto.setBase("%fp");
                    
               offsetCnt++;
               int val = -offsetCnt * t.getSize();
               String value = String.valueOf(val);
               sto.setOffset(value);
           }
                    
           int i;

           if(t instanceof FloatType){
               // static case
               if(optstatic != null){
                   float f = exp.getFloatValue();
                   codegen.DoGlobalVarInitLit(sto, String.valueOf(f), sto.getOffset(), optstatic);
               }
               // nonstatic case
               else{
                   if(exp.getLitTag()){
                       codegen.DoVarAssign(sto, expr, null);
                   }
                   else{
                       codegen.DoFloatAssign(sto, expr, null);
                   }
               }
           }
           else if(t instanceof IntType){
               i = exp.getIntValue();
               String str = String.valueOf(i);
               // static case
               if(optstatic != null){
                   codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic);
               }
               // nonstatic case
               else{
                   codegen.DoConstAssign(sto, str, exp.getName());
               }
           }
           else if(t instanceof BoolType){
               i = exp.getBoolValue() ? 1 : 0;
               String str = String.valueOf(i);
               // static case
               if(optstatic != null){
                   codegen.DoGlobalVarInitLit(sto, str, sto.getOffset(), optstatic);
               }
               // nonstatic case
               else{
                   codegen.DoConstAssign(sto, str, exp.getName());
               }
           }
           // to be implement array, struct etc  

        }

		m_symtab.insert(sto);
	}


    //------------------------
    //
    //------------------------
    void SelfStruct(String id){
        StructdefSTO sto = new StructdefSTO(id, new StructType(id));
        m_symtab.setStruct(sto);
    }
	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoStructdefDecl(String id)
	{
		if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
		}
         
        StructType scopeStruct = new StructType(id);
        scopeStruct.setScope(scope);
       	StructdefSTO sto = new StructdefSTO(id, scopeStruct);
        Vector<STO> locals = scope.getLocals();
        int size = 0;
        
        for(int i = 0; i < locals.size(); i++){
            
            STO elm = locals.get(i);
            if(elm instanceof FuncSTO) {
                  if(elm.getType() instanceof PointerType){
                      if( ((PointerType)elm.getType()).getBaseType() instanceof StructType){
                          if( ((PointerType)elm.getType()).getBaseType().getName().equals(scopeStruct.getName()) ) {
                              ((PointerType)locals.get(i).getType()).setBaseType(scopeStruct);
                          }
                      }
                  }

                sto.getFuncs().addElement(elm);
            }
            else {
                sto.getVars().addElement(elm);
                if(elm.getType() instanceof ArrayType) {
                   size = size + ((ArrayType)elm.getType()).getTotalSize();
                }
                else {
                  size = size + elm.getType().getSize();
                  if(elm.getType() instanceof PointerType){
                      if( ((PointerType)elm.getType()).getBaseType() instanceof StructType){
                          if( ((PointerType)elm.getType()).getBaseType().getName().equals(scopeStruct.getName()) ) {
                              ((PointerType)locals.get(i).getType()).setBaseType(scopeStruct);
                          }
                      }
                  }
                } 
            }

        }
        sto.getType().setSize(size);


        //reset offset in struct counter
        structOffset = 0;
		m_symtab.insert(sto);
        ///m_symtab.setStruct(sto);
	}

    //----------------------------------------------------------------
    // Crafty way to get struct name
    //----------------------------------------------------------------
    void DoStructName(String name){
        StructName = name;
    }

    String getStructName(){
        return StructName;
    }

    //----------------------------------------------------------------
    //
    //----------------------------------------------------------------
    void DoDefaultConstructor(){
        if (m_symtab.accessLocal(StructName) == null)
        {
            FuncSTO sto = new FuncSTO(StructName, new StructType(StructName));
            //tag it
            sto.setOTag(true);
            
            m_symtab.insert(sto);
            Scope def = m_symtab.getCurrScope();
            ((StructType)m_symtab.getStruct().getType()).setScope(def);
		        m_symtab.openScope();
		        m_symtab.setFunc(sto);
            this.DoFormalParams(new Vector<String>());
            this.DoFuncDecl_2();

            
        }

        if (m_symtab.accessLocal("~"+StructName) == null)
        {
            FuncSTO sto = new FuncSTO("~"+StructName, new StructType(StructName));
            //tag it
            sto.setOTag(true);
            
            m_symtab.insert(sto);
            Scope def = m_symtab.getCurrScope();
            ((StructType)m_symtab.getStruct().getType()).setScope(def);
		        m_symtab.openScope();
		        m_symtab.setFunc(sto);
            this.DoFormalParams(new Vector<String>());
            this.DoFuncDecl_2();

            
        }
        else {
            return;
        }
    }
	//----------------------------------------------------------------
	// Struct Constructor Dtor
	//----------------------------------------------------------------
	void DoStructorDecl(String id)
	{
        // destructor
        if (id.charAt(0) == '~'){
            String s = id.substring(1);
            if( !s.equals(StructName)){
                m_nNumErrors++;
			    m_errors.print(Formatter.toString(ErrorMsg.error13b_Dtor, id, StructName));
                
            }
            if (m_symtab.accessLocal(id) != null)
		    {
            	m_nNumErrors++;
			    m_errors.print(Formatter.toString(ErrorMsg.error9_Decl, id));
                
		    }

        }
        // constructor
        else {
            if (!id.equals(StructName) ){
                m_nNumErrors++;
			    m_errors.print(Formatter.toString(ErrorMsg.error13b_Ctor, id, StructName));
                
            }
        }
        String typeid = id;
        if(id.contains("~")) {
            typeid = id.substring(1);
        }
		FuncSTO sto = new FuncSTO(id,new StructType(typeid));
        sto.setReturnType(new VoidType("void",0));
        sto.setOTag(true);
		   m_symtab.insert(sto);
        Scope ctor = m_symtab.getCurrScope();
        ((StructType)m_symtab.getStruct().getType()).setScope(ctor);
		m_symtab.openScope();

		m_symtab.setFunc(sto);

        if(id.contains("~")) {
           codegen.DoFuncStart(sto,"%g1",typeid);
           codegen.DoDtorParam();
        }
    
	}


    //----------------------------------------------------------------
	// Original
	//----------------------------------------------------------------
	void DoFuncDecl_1(String id,Type t)
	{
        if (m_symtab.accessLocal(id) != null)
		{
			m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
		}
	
		FuncSTO sto = new FuncSTO(id,t);
	    sto.setReturnType(t);
        sto.setIsExtern(true);

        m_symtab.insert(sto);
        m_symtab.addFunc(sto);
        
		m_symtab.openScope();
		m_symtab.setFunc(sto); 

	}



    //----------------------------------------------------------------
    // helper method to check if it's in struct
    //----------------------------------------------------------------
    void IsInStruct(){ isInStruct = !isInStruct;}

    public boolean getIsInStruct() {
    	return isInStruct;
    }

    //---------------------------------------------------------------
    // helper method that resolves multi-errors in same line for struct
    //---------------------------------------------------------------

    void DoFuncDecl_3(String id, Type t, Object o)
	{
        isMultiError = false;
        String s = o.toString();
		if (m_symtab.accessLocal(id) != null)
		{
            if(isInStruct){
                if (!(m_symtab.accessLocal(id) instanceof FuncSTO)){
                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error13a_Struct, id));
                    isMultiError = true;
                
                }
                
            }
            else {
                if (!(m_symtab.accessLocal(id) instanceof FuncSTO)) {
                    m_nNumErrors++;
			        m_errors.print(Formatter.toString(ErrorMsg.redeclared_id, id));
                }

            }
            
		}
		FuncSTO sto = new FuncSTO(id, t); 
        if(s == "&"){
           sto.flag = true;
        }
        sto.setReturnType(t);
        sto.setOTag(true); // tag to exclude self from overload check
        m_symtab.insert(sto);

        //handle struct func declaration
        if(isInStruct){ //  check if func is in struct decl
            Scope fun = m_symtab.getCurrScope();
            ((StructType)m_symtab.getStruct().getType()).setScope(fun);

        }
        //regular func overload
        else{
            m_symtab.addFunc(sto);
        }

        
        // assembly for function group 
        if(m_symtab.OverloadCheck(id).size() == 1) {
           codegen.FuncGroup(sto);
        }


		m_symtab.openScope();
		m_symtab.setFunc(sto);

        // initialize offset counter for local vars in func 
        offsetCnt = 0;



	}


	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoFuncDeclExtern()
	{

		//WRITE ASSEMBLY:
        // the end of the function
        FuncSTO fun = m_symtab.getFunc();

        int val = offsetCnt * 4;
        fun.setOffset(" + " + String.valueOf(val));

        fun.setBase("92");

        offsetCnt = 0;     //reset counter after each init 
        
        m_symtab.closeScope();

		m_symtab.setFunc(null);
	}


	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoFuncDecl_2()
	{

		//WRITE ASSEMBLY:
        // the end of the function
        FuncSTO fun = m_symtab.getFunc();


        int val = offsetCnt * 4;
        fun.setOffset(" + " + String.valueOf(val));

        fun.setBase("92");

        if(isInStruct){
            codegen.DoFuncEnd(fun, StructName);
            codegen.RetRestoreStruct();
            offsetCnt = 0;     //reset counter after each init -- 11/19 

        }
        else{
            codegen.DoFuncEnd(fun, null);
            codegen.DoDtorLocalCall();

            if(m_symtab.getFunc().getName().equals("main")) {
                codegen.DoDtorCallGlobal();
            }
            else{
                codegen.RetRestore();
            }
            offsetCnt = 0;     //reset counter after each init -- 11/19 

        }
        m_symtab.closeScope();



		m_symtab.setFunc(null);
	}

	void DoFormalParamsExtern(Vector<String> params)
	{
        int paramCnt = 0;
		
        if (m_symtab.getFunc() == null)
		{
			m_nNumErrors++;
			m_errors.print ("internal: DoFormalParams says no proc!");
		}

		// insert parameters here
        FuncSTO sto = m_symtab.getFunc();

        for(int i = 0; i < params.size(); i++){

            STO par = this.ProcessParams(params.get(i));

            //Write Assembly: add base and offset to params
            par.setOffset(String.valueOf(68+paramCnt*4));
            paramCnt++;
            par.setBase("%fp");


            sto.addParam(par);
            m_symtab.insert(par);
        }
        
    }

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoFormalParams(Vector<String> params)
	{
        int paramCnt = 0;
		
        if (m_symtab.getFunc() == null)
		{
			m_nNumErrors++;
			m_errors.print ("internal: DoFormalParams says no proc!");
		}

		// insert parameters here
        FuncSTO sto = m_symtab.getFunc();

        
        // Add "this" parameter for struct
        if(isInStruct) {
           paramCnt++;
        }

        for(int i = 0; i < params.size(); i++){

            STO par = this.ProcessParams(params.get(i));

            //Write Assembly: add base and offset to params
            par.setOffset(String.valueOf(68+paramCnt*4));
            paramCnt++;
            par.setBase("%fp");


            sto.addParam(par);
            m_symtab.insert(par);
        }



        //WRITE ASSEMBLY
        //the start of the function
        if(isInStruct){
             codegen.DoFuncStart(sto, "%g1", StructName);
             sto.setStructTag(true);
             sto.setStructName(StructName);
         
        }
        else{
            codegen.DoFuncStart(sto, "%g1", null);
        }

        if(isInStruct) {
           codegen.DoThisParam();

        }

        codegen.DoParams(sto);

        //sto.setTag(true); // figure out what flag is for

        Vector<STO> over = new Vector<STO>();
        boolean overloadErr = false;
        boolean next;
        if(isInStruct) {  //check in struct decl  
            over = ((StructType)m_symtab.getStruct().getType()).OverloadCheckStruct( sto.getName() );// grab all func with same name, excludes itself
           ((StructType)m_symtab.getStruct().getType()).OffStructTag();//tag of struct tag for self
        }
        else{
            over = m_symtab.OverloadCheckParam(sto.getName()); // get all function with same name, excludes itself
            m_symtab.TagOff();
        }

        if (over.isEmpty()) { // non overload case
           m_symtab.TagOff(); // turn off tag to exclude current function
           return;
        }
        else { // overload case 
           Vector<STO> newParams = sto.getParams(); // the new params to be checked

           for (int i = 0; i < over.size();i++) { // loop thru functions
               overloadErr = false; //if first iter doesn't find a match, the flag should reset for the sec iter
              Vector<STO> storedParams = ((FuncSTO)over.get(i)).getParams(); // params currently in table
              if(newParams.size() == storedParams.size() ) { // if same number params need to check each one
                  for (int j = 0; j < newParams.size(); j++ ) { // loop thru params
               //overloadErr = false;

                     Type newPar = newParams.get(j).getType();
                     Type storePar = storedParams.get(j).getType();

                     if(!newPar.isEquivalent(storePar)) { // if params are not equivalent then OK

                       overloadErr = true;
                       next = true;
                       break; 
                     }

                  }
                  if(!overloadErr){
                    break;
                  }


              }
              else { // if different number of params OK
                  overloadErr = true;
                  continue;

              }
           }

           if(isMultiError){//check if there is error prev to overload check 
              isMultiError = false;
              return;  
           }

           if(!overloadErr) { // if params are the same then overload error
             m_nNumErrors++;
             m_errors.print(Formatter.toString(ErrorMsg.error9_Decl, sto.getName()));
             m_symtab.removeFunc(sto);
             m_symtab.TagOff();
             //((StructType)sto.getType()).OffStructTag(); //turn of struct tag for self
             return;
           }
        }

	}


    public Vector<STO> overloadFuncs(Vector<STO> functions,String funcName) {
        Vector<STO> overloaded = new Vector<STO>();
        for (int i =0; i < functions.size(); i++) {
            
            if (funcName.equals(functions.elementAt(i).getName()) && functions.get(i) instanceof FuncSTO   ) {
                if(((FuncSTO)functions.get(i)).isTag() == false) {
                  overloaded.add(functions.elementAt(i));
                }
            }
        }
        return overloaded;    
    }

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoBlockOpen()
	{
		// Open a scope.
		m_symtab.openScope();
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	void DoBlockClose()
	{
		m_symtab.closeScope();
	}

    //---------------------------------
    // get current scope in struct
    //---------------------------------
    void getStructScope(){
        scope = m_symtab.getCurrScope();
    }


  //
  // IN USE
  //
  STO DoAssignTypeCheck(STO a, STO b) {
      
        
        if(a instanceof ErrorSTO) {
            return a;
        }
        else if (b instanceof ErrorSTO) {
            return b;
        }

       
 
        
        if ((!a.isModLValue())) 
		    {
			// Good place to do the assign checks
            m_errors.print(ErrorMsg.error3a_Assign);
            m_nNumErrors++;
            return new ErrorSTO(a.getName());
		    }

        STO result;
        if (!b.getType().isAssignable(a.getType())) {
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error3b_Assign,b.getType().getName(),a.getType().getName())); 
            return new ErrorSTO(a.getName()); 
           
                   
        }


        // Assembly Writing: code for var assignment
        // note this only takes place in local not global scope
        if(this.GetSavedLineCnt() == 0){
            this.SaveLineCnt(); 
        }
        if(this.GetSavedLineCnt() != this.GetLineNum()){
        
            if(codegen.getholdOff()){
                codegen.TimeToWrite();
            }
            this.SaveLineCnt();
            codegen.setholdOff(false);
        }
        else{
            codegen.setholdOff(true);
        }

        // change it to lit only
        if(b instanceof ConstSTO && !((ConstSTO)b).getLitTag()){
    
            //float case
            if(a.getType() instanceof FloatType){
                // type promotion
                if(b.getType() instanceof IntType){
                    STO promote = new ExprSTO("temp");
                    offsetCnt++;
                    int value = -offsetCnt*4;
                    promote.setOffset(String.valueOf(value));
                    promote.setBase("%fp");
                    
                    codegen.DoFloatAssign(a, b, promote);
                        
                }
                else{
                    codegen.DoFloatAssign(a, b, null);
                }
            }
            //int case
            else if(a.getType() instanceof IntType){ 
                int val = ((ConstSTO)b).getIntValue();
                String s = String.valueOf(val);
                codegen.DoConstAssign(a, s, b.getName());
            }
            //bool case
            else if(a.getType() instanceof BoolType){
                int val = ((ConstSTO)b).getBoolValue() ? 1 : 0;
                String s = String.valueOf(val);
                codegen.DoConstAssign(a, s, b.getName());
            }
            // array case
            else{
      
                ArrayType t = (ArrayType)a.getType();
                Type baseType = t.getBaseType();
                if(baseType instanceof IntType ){
                    int val = ((ConstSTO)b).getIntValue();
                    codegen.DoConstAssign(a, String.valueOf(val), b.getName());
                    
                }
                else if(baseType instanceof BoolType){
                    int val = ((ConstSTO)b).getBoolValue() ? 1 : 0;
                    codegen.DoConstAssign(a, String.valueOf(val), b.getName());
                }
                else if(baseType instanceof FloatType){
                    if(b.getType() instanceof IntType){
                        STO promote = new ExprSTO("temp");
                        offsetCnt++;
                        int value = -offsetCnt*4;
                        promote.setOffset(String.valueOf(value));
                        promote.setBase("%fp");
                    
                        codegen.DoFloatAssign(a, b, promote);
                    
                    }
                    else{
                        float val = ((ConstSTO)b).getFloatValue();
                        codegen.DoFloatAssign(a, b, null);
                    }
                }


            }
        }
        else{
            
            //Type promotion case
            if(a.getType() instanceof FloatType && b.getType() instanceof IntType){
                // This does nothing except holds offset and base 
                STO promote = new ExprSTO("temp");
                offsetCnt++;
                int value = -offsetCnt*4;
                promote.setOffset(String.valueOf(value));
                promote.setBase("%fp");

                codegen.DoVarAssign(a, b, promote);
                  
            }
            else if(a.getType() instanceof StructType){
                codegen.DoStructAssign(a, b);
            }
            else{
       
                codegen.DoVarAssign(a, b, null);
            }

            
            
        }


        result = new ExprSTO(a.getName() + "=" + b.getName(), a.getType());
        if(a.getBase() == "%g0"){
            result.setBase("%g0");
            result.setOffset(a.getName());
        }
        else{
            result.setBase("%fp");
            result.setOffset(a.getOffset());
            
        }

        // pass along tags
        result.setArrayTag(a.getArrayTag());
        result.setStructTag(a.getStructTag());
        result.setIsPointer(a.getIsPointer());
        result.flag = a.flag;
        
        result.setIsAddressable(false);
        result.setIsModifiable(false);

        return result;
    
    }

    //----------------------------------------------------------------
    // Helper method to get struct info for FuncCall
    // Added 10/29
    //----------------------------------------------------------------
     
    public void setCallingStruct(STO name) {
    	//callingStruct = m_symtab.accessGlobal(name.getType().getName());
      callingStruct = name;
    }


    //----------------------------------------------------------------
	// 
	//----------------------------------------------------------------

    void getName(String s)
    {
        name = s;
    }
	//----------------------------------------------------------------
	// 
	//----------------------------------------------------------------
	STO DoFuncCall(STO sto, Vector<STO> params)
	{
       if(sto instanceof ErrorSTO){
          return sto;
       }
       else if ( !sto.isFunc() ) {
	      m_nNumErrors++;
		    m_errors.print(Formatter.toString(ErrorMsg.not_function, sto.getName()));
		    return new ErrorSTO(sto.getName());
	     }
       else {
       	   Vector<STO> overloaded;
            
           if(this.getStructFunCall() == true) {
              //overloaded = ((StructType)m_symtab.getStruct().getType()).OverloadCheckStructCall(sto.getName());
              overloaded = ((StructType)callingStruct.getType()).OverloadCheckStructCall(sto.getName());
              this.setStructFunCall(false);	
           }
           else {
              overloaded = m_symtab.OverloadCheckFun(sto.getName()); //checks if func exists in table, also gets overload if any
           } 

           STO fun; // var for current func
           Vector<STO> funPar; // vector for current funcs params
           STO curPar; // calling param being check
           STO funsCurPar; // cun func para in table being checked against 
           Type curType; // the type of calling param
           Type funsCurType; // the type of the func param
           if(overloaded.isEmpty()) {
              // do something
              System.out.println("In");
           }
           else if (overloaded.size() == 1) { // non overload case
              int overParSize = ((FuncSTO)overloaded.get(0)).getParams().size();
              int parSize = params.size();
              fun = overloaded.get(0); // get the function from the table
              if(overParSize != parSize) { // if have different number of params print error
                 m_nNumErrors++;
		         m_errors.print(Formatter.toString(ErrorMsg.error5n_Call,parSize,overParSize));
		         return new ErrorSTO(sto.getName());
              }
              else {
                  if(overParSize == 0 && parSize == 0) { // case if calling function has no params
                     STO result = new ExprSTO(fun.getName(),fun.getType());
                     if(fun.flag == true) { // return by ref set to Mod L
                        result.setIsModifiable(true);
                        result.setIsAddressable(true);
                        result.flag = true;
                     }
                     else { // if return by value set to R val
                        result.setIsModifiable(false);
                        result.setIsAddressable(false);
                     
                     }
                     if(((FuncSTO)fun).getReturnType() instanceof VoidType){

                         codegen.DoFuncCallNoParamVoid(fun);
                     }
                     else{
                         offsetCnt ++;
                         int val = -offsetCnt * ((FuncSTO)fun).getReturnType().getSize();
                         String offset = String.valueOf(val);
                         result.setOffset(offset);
                         result.setBase("%fp");

                         if(this.GetSavedLineCnt() == 0){
                             this.SaveLineCnt();
                         }
                         if(this.GetSavedLineCnt() != this.GetLineNum()){
                             if(codegen.getholdOff()){
                                 codegen.TimeToWrite();
                             }
                             this.SaveLineCnt();
                             codegen.setholdOff(false);
                         }
                         else{
                             codegen.setholdOff(true);
                         }

                         codegen.DoFuncCallNoParam(result, fun);

                         
                         
                     }
                     return result;
                  }
                  else { // case if nonzero params 
                     return this.DoFunctionCall(sto,params,overloaded); // helper function 
                  }
              }
           }
           else { // overloadcase
               return this.DoOverloadCall(sto,params,overloaded);
           
           }

          return sto;
       }
	}

    //-----------------------------------------------------------------------------
    // Functions to check if there are errors in param passed in
    //
    //-----------------------------------------------------------------------------
    public STO paramErrorCheck(Vector<STO> params) {
        for(int i=0; i < params.size();i++) {
           if(params.get(i) instanceof ErrorSTO || params.get(i).getType() instanceof ErrorType) {
              return params.get(i);
           }
        }
        return new ExprSTO("okay");
    
    }

    //-----------------------------------------------------------------------------
    // Helper function to get Max number of Params
    //
    //-----------------------------------------------------------------------------
    public int findMaxParams(Vector<STO> funcList) {
        int max = 0;
        for (int i = 0; i < funcList.size(); i++) {
            STO func = funcList.get(i);
            if (max < ((FuncSTO)func).getParams().size()) {
                max = ((FuncSTO)func).getParams().size();
            }
        }
        return max;
    }

    public int findMinParams(Vector<STO> funcList) {
        int min = funcList.get(0).getParams().size();
        for (int i = 0; i < funcList.size(); i++) {
            STO func = funcList.get(i);
            if (min > ((FuncSTO)func).getParams().size()) {
                min = ((FuncSTO)func).getParams().size();
            }
        }
        return min;
    }

    //-----------------------------------------------------------------------------
    // Helper Function for checking function call
    //-----------------------------------------------------------------------------
    public STO DoFunctionCall(STO func, Vector<STO> params, Vector<STO> overloaded) {

           STO err;
           if(func instanceof ErrorSTO || func.getType() instanceof ErrorType) {
              return func;
           }

           if((err=this.paramErrorCheck(params)) instanceof ErrorSTO) {
              return err;
           }
  

           STO result = new ExprSTO("default");
           STO fun; // var for current func
           Vector<STO> funPar; // vector for current funcs params
           STO curPar; // calling param being check
           STO funsCurPar; // cun func para in table being checked against 
           Type curType; // the type of calling param
           Type funsCurType; // the type of the func param
           int overParSize = ((FuncSTO)overloaded.get(0)).getParams().size();
           

           int parSize = params.size();
           int match = 0;
           for(int i = 0; i < overloaded.size();i++) { // loop thru overloaded funcs
              fun = overloaded.get(i);
              funPar = fun.getParams();
              match = 0;
              for(int j = 0; j < params.size();j++) { // loop thru params check if they match
                 curPar = params.get(j);
                 curType = curPar.getType();
                 funsCurPar = funPar.get(j);
                 funsCurType = funsCurPar.getType();

                 
                 /* Begin Error checks  */
                 if(funsCurPar.flag == true) { //if param if pass by ref
                    if(!curType.isEquivalent(funsCurType)) { // if params are not equivalent then error since pass by ref
                       match = 0; 
                       m_nNumErrors++;
                       m_errors.print(Formatter.toString(ErrorMsg.error5r_Call,curType.getName(),funsCurPar.getName(),funsCurType.getName()));
                       result = new ErrorSTO("error");
                    }
                    else if(!curPar.isModLValue() && !(curType instanceof ArrayType)) { // pass in arg is not L val then error
                       match = 0;
                       m_nNumErrors++;
                       m_errors.print(Formatter.toString(ErrorMsg.error5c_Call,funsCurPar.getName(),funsCurType.getName()));
                       result = new ErrorSTO("error");
               
                    }

                    else {
                        match++; // success case
                    }
                 }
                 else { // if param is pass by val
                    if(!curType.isAssignable(funsCurType)) { // if params are not equivalent then error since pass by ref
                       match = 0;
                       m_nNumErrors++;
                       m_errors.print(Formatter.toString(ErrorMsg.error5a_Call,curType.getName(),funsCurPar.getName(),funsCurType.getName()));
                       result =  new ErrorSTO("error");
                    }
                    else {
                        match++; // sucess case
                    }
                 }
                 /* End of ErrorChecks */                     
              }
              if(match == params.size()) {
                 result = new ExprSTO(fun.getName(),fun.getType());
                 // Write Assembly: set offset and base


                 if(this.GetSavedLineCnt() == 0){
                     this.SaveLineCnt();
                 }
                 if(this.GetSavedLineCnt() != this.GetLineNum()){
                     if(codegen.getholdOff()){
                         codegen.TimeToWrite();
                     }
                     this.SaveLineCnt();
                     codegen.setholdOff(false);
                 }
                 else{
                     codegen.setholdOff(true);
                 }

                 if(!(func.getType() instanceof StructType)) {
                     if(!(((FuncSTO)fun).getReturnType() instanceof VoidType)){
                         offsetCnt ++;
                     }
        
                   int val = -offsetCnt * ((FuncSTO)fun).getReturnType().getSize();
                
                   String offset = String.valueOf(val);
                   result.setOffset(offset);
                   result.setBase("%fp");
               
                   offsetCnt = codegen.DoFuncCallParam(result, fun, params, offsetCnt);


                 }


                 // - end
                 if(fun.flag == true) { // return by ref set to Mod L
                    result.setIsModifiable(true);
                    result.setIsAddressable(true);
                    result.flag = true;
                    return result;
                 }
                else { // if return by value set to R val
                    result.setIsModifiable(false);
                    result.setIsAddressable(false);
                    return result;
                     
                }

              }

           }

           return result;
    }

    public STO DoOverloadCall(STO func, Vector<STO> params, Vector<STO> overloaded) {
       STO result = new ExprSTO("default");
       STO fun; // var for current func
       Vector<STO> funPar; // vector for current funcs params
       STO curPar; // calling param being check
       STO funsCurPar; // cun func para in table being checked against 
       Type curType; // the type of calling param
       Type funsCurType; // the type of the func param
       int overParSize;
       int parSize = params.size();
       int match = 0;

       int maxOverSize = this.findMaxParams(overloaded); // max num of params
       int minOverSize = this.findMinParams(overloaded); // min num of params
       if(params.size() > maxOverSize) {
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error9_Illegal,func.getName()));
           result =  new ErrorSTO("error");
           return result;
       }
       else if(params.size() < minOverSize) {
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error9_Illegal,func.getName()));
           result =  new ErrorSTO("error");
           return result;
       }
       for(int i = 0; i < overloaded.size();i++) { // loop thru overloaded funcs
              fun = overloaded.get(i);
              funPar = fun.getParams();
              match = 0;
              overParSize = ((FuncSTO)overloaded.get(i)).getParams().size();

              // case with no params
              if(parSize == overParSize) {
              	if(overParSize == 0 && parSize == 0) { // case if calling function has no params
                  this.setSomeFunc(fun);
                  result.setStructName(fun.getStructName());
                  result.setAssemblyName(fun.getAssemblyName());
              	  result =  new ExprSTO(fun.getName(),fun.getType());
                  if(((FuncSTO)fun).getReturnType() instanceof VoidType){
                      if(!(func.getType() instanceof StructType)){  
                          codegen.DoFuncCallNoParamVoid(fun);
                      }
                
                  }
                  else{
                      if(!(func.getType() instanceof StructType)){  
                        // Write Assembly: set offset and base
                         offsetCnt ++;
                         int val = -offsetCnt * ((FuncSTO)fun).getReturnType().getSize();
                         String offset = String.valueOf(val);
                         result.setOffset(offset);
                         result.setBase("%fp");

                         if(this.GetSavedLineCnt() == 0){
                             this.SaveLineCnt();
                         }
                         if(this.GetSavedLineCnt() != this.GetLineNum()){
                             if(codegen.getholdOff()){
                                 codegen.TimeToWrite();
                             }
                             this.SaveLineCnt();
                             codegen.setholdOff(false);
                         }
                         else{
                             codegen.setholdOff(true);
                         }
                          // - end

                          codegen.DoFuncCallNoParam(result, fun);
                      }
                      
                  }
              	  if(fun.flag == true) { // return by ref set to Mod L
              	     result.setIsModifiable(true);
              	     result.setIsAddressable(true);
              	     result.flag = true;
                     return result;
              	  }
              	  else { // if return by value set to R val
              	     result.setIsModifiable(false);
              	     result.setIsAddressable(false);
              	     return result;
              	       
              	  }

              	  /* End of no param case */
              	}
              	else {

              		for(int j = 0; j < params.size();j++) { // loop thru params check if they match
              		   curPar = params.get(j);
              		   curType = curPar.getType();
              		   funsCurPar = funPar.get(j);
              		   funsCurType = funsCurPar.getType();

              		   
              		   /* Begin Error checks  */
              		   if(funsCurPar.flag == true) { //if param if pass by ref
              		      if(!curType.isEquivalent(funsCurType)) { // if params are not equivalent then error since pass by ref
              		         match = 0; 
              		         //m_nNumErrors++;
              		         //m_errors.print(Formatter.toString(ErrorMsg.error9_Illegal,func.getName()));
              		         //result = new ErrorSTO("error");
              		         //return result; // maybe ?
              		      }
              		      else if(!curPar.isModLValue() && !(curType instanceof ArrayType)) { // pass in arg is not L val then error
              		         match = 0;
              		         //m_nNumErrors++;
              		         //m_errors.print(Formatter.toString(ErrorMsg.error9_Illegal,func.getName()));
              		         //result = new ErrorSTO("error");
              		         //return result;
              		 
              		      }

              		      else {
              		          match++; // success case

              		      }
              		   }
              		   else { // if param is pass by val
              		      if(!curType.isEquivalent(funsCurType)) { // if params are not equivalent then error since pass by ref
              		         match = 0;
              		         //m_nNumErrors++;
              		         //m_errors.print(Formatter.toString(ErrorMsg.error9_Illegal,func.getName()));
              		         //result =  new ErrorSTO("error");
              		         //return result;
              		      }
              		      else {
              		          match++; // sucess case
              		      }
              		   }
              		   /* End of ErrorChecks */                     
              		}
              		if(match == params.size()) {
                      //result.setStructName(fun.getStructName());
                      //result.setAssemblyName(fun.getAssemblyName());
                      this.setSomeFunc(fun);
              		  result = new ExprSTO(fun.getName(),fun.getType());
                      //result = fun;
                      if(!(func.getType() instanceof StructType)){  

                         // Write Assembly: set offset and base
                         offsetCnt ++;
                         int val = -offsetCnt * ((FuncSTO)fun).getReturnType().getSize();
                         String offset = String.valueOf(val);
                         result.setOffset(offset);
                         result.setBase("%fp");

                         if(this.GetSavedLineCnt() == 0){
                             this.SaveLineCnt();
                         }
                         if(this.GetSavedLineCnt() != this.GetLineNum()){
                             if(codegen.getholdOff()){
                                 codegen.TimeToWrite();
                             }
                             this.SaveLineCnt();
                             codegen.setholdOff(false);
                         }
                         else{
                             codegen.setholdOff(true);
                         }

                         offsetCnt = codegen.DoFuncCallParam(result, fun, params, offsetCnt);
                         // - end
                       }
              		   if(fun.flag == true) { // return by ref set to Mod L
              		      result.setIsModifiable(true);
              		      result.setIsAddressable(true);
              		      return result;
              		   }
              		  else { // if return by value set to R val
              		      result.setIsModifiable(false);
              		      result.setIsAddressable(false);
              		      return result;              		       
              		  }

              		}

              	}

              }
              else {
              	continue;
              }          
           }
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error9_Illegal,func.getName()));
           result =  new ErrorSTO("error");
           return result;

       //return result;

        
    }

    void toggleInThisFlag()
    {
        inThisFlag = !inThisFlag;
    }

    public STO getCurrentStruct() {
       return m_symtab.getStruct();
    }
	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	STO DoDesignator2_Dot(STO sto, String strID)
	{
        
        if( sto instanceof ErrorSTO){
            inThisFlag = false;
            return sto;
        }
        

		// Good place to do the struct checks
        if(!(sto.getType() instanceof StructType) ){
            inThisFlag = false;
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error14t_StructExp, sto.getType().getName()));
            return new ErrorSTO("error");
   
        }


        if(inThisFlag == true){
            Scope curr =  ((StructType)sto.getType()).getScope();



            //Scope curr = ((StructType)m_symtab.getStruct().getType()).getScope();
            Vector<STO> locals = curr.getLocals();
            for(int i = 0 ; i < locals.size(); i++){
                if(locals.get(i).getName().equals(strID)){
                    toggleInThisFlag();
                    if(locals.get(i) instanceof FuncSTO) {
                      this.setStructFunCall(true);
                    }
                    STO result= locals.get(i);
                    
                    result.setStructTag(locals.get(i).getStructTag());
                    if(!(result instanceof FuncSTO)){
                       offsetCnt++;
                       result.setOffset(String.valueOf(offsetCnt * -4));
                       result.setBase("%fp");
                       codegen.DoThisCall(result);
                    }
                    return result;
                }
             }
             m_nNumErrors++;
             m_errors.print(Formatter.toString(ErrorMsg.error14c_StructExpThis ,strID));
             toggleInThisFlag();
             return new ErrorSTO("error"); 
 
        }
        else{
            Scope curr = ((StructType)sto.getType()).getScope();
            Vector<STO> locals = curr.getLocals();
            for(int i = 0 ; i < locals.size(); i++){
                if(locals.get(i).getName().equals(strID)){
                    if(locals.get(i) instanceof FuncSTO) {
                      this.setStructFunCall(true);
                      codegen.DoCtorThis(sto);
                      return locals.get(i);
                    }
                    // Assembly write: struct call
                    STO result = new VarSTO(locals.get(i).getName(), locals.get(i).getType());
                    if(!(locals.get(i) instanceof FuncSTO)){
                        // pass along all the tag
                        result.flag = locals.get(i).flag;
                     
                        result.setStructTag(locals.get(i).getStructTag());
                        result.setArrayTag(locals.get(i).getArrayTag());
                        result.setStructOffset(locals.get(i).getStructOffset());
                        // add 11/28 sto.getIsPointer
                        if(locals.get(i).getType() instanceof PointerType || sto.getIsPointer() ){
                            result.setIsPointer(true);
                        }
                        if(sto.getArrayTag()) {
                          result.setArrayTag(true);
                        }
                        offsetCnt++;
                        result.setOffset(String.valueOf(offsetCnt * -4));
                        result.setBase("%fp");
                        codegen.DoStructCall(sto, result);
                    }
                    return result;
                }
             }
             m_nNumErrors++;
             m_errors.print(Formatter.toString(ErrorMsg.error14f_StructExp ,strID, sto.getType().getName()));
             return new ErrorSTO("error"); 

        
        }
        
        

/*
        if((sto.getName().equals("this")) && sto.getType() instanceof StructType){
           // isThis = true;
            Scope scope = m_symtab.getAboveScope();
            Vector<STO> locals = scope.getLocals();
            for(int j = 0; j < locals.size(); j++){
                if(locals.get(j).getName().equals(strID)){
                    locals.get(j).setIsModifiable(true);
                    locals.get(j).setIsAddressable(true);
                    locals.get(j).setIsThis(true);
                    if(locals.get(j) instanceof FuncSTO){
                         ((FuncSTO)locals.get(j)).setIsStruct(true);
                    }
                    m_symtab.setStruct((StructdefSTO)sto); // add
                    m_symtab.insert(locals.get(j));
                    return locals.get(j);
                }

            }

        }
       
       
        if((sto.getType() instanceof StructType) && (!sto.getName().equals("this"))) {
               // isThis = false;

                Vector<STO> fun = ((StructdefSTO)sto).getFuncs();
                Vector<STO> var = ((StructdefSTO)sto).getVars();


                for(int i = 0; i < fun.size(); i++){
                   if(fun.get(i).getName().equals(strID)){

                       fun.get(i).setIsModifiable(true);
                       fun.get(i).setIsAddressable(true);
                       ((FuncSTO)fun.get(i)).setIsStruct(true);
                       m_symtab.setStruct((StructdefSTO)sto); // add
                       m_symtab.insert(fun.get(i));
                       return fun.get(i);
                   }
                }
                for(int i = 0; i < var.size(); i++){
                   if(var.get(i).getName().equals(strID)){
                       var.get(i).setIsModifiable(true);
                       var.get(i).setIsAddressable(true);
                       m_symtab.insert(var.get(i));
                       return var.get(i);

                   }
                }

                m_nNumErrors++;
                m_errors.print(Formatter.toString(ErrorMsg.error14f_StructExp,strID, sto.getType().getName()));
                return new ErrorSTO("error");
        }
        //isThis = false;
	    return sto;*/
	}

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	STO DoDesignator2_Array(STO sto, STO expr)
	{
		// Good place to do the array checks
        if (sto instanceof ErrorSTO){
            return sto;
        }
        else if(expr instanceof ErrorSTO){
            return expr;
        }

        STO ptr = this.DoNullPointerCheck(sto);
        if(ptr instanceof ErrorSTO){
            return ptr;
        }

        
        if (!(sto.getType() instanceof ArrayType) && !(sto.getType() instanceof PointerType)){
            
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error11t_ArrExp, sto.getType().getName()));
            return new ErrorSTO("error");
            
        }
        else if(!(expr.getType().isEquivalent(new IntType("int")))){
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error11i_ArrExp, expr.getType().getName()));
            return new ErrorSTO("error");
        }

        else if(expr instanceof ConstSTO && !(sto.getType() instanceof PointerType)){
            Type temp = sto.getType();
            if(temp instanceof ArrayType){
                if (temp.getSize()-1 < ((ConstSTO)expr).getIntValue()  || ( (ConstSTO )expr).getIntValue() < 0  ){
                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error11b_ArrExp, ((ConstSTO) expr).getIntValue(),temp.getSize()));
                    return new ErrorSTO("Error");
                }

            }
        }
        if(sto.getType() instanceof ArrayType) {
            VarSTO v = new VarSTO(sto.getName()+"["+expr.getName()+"]", ((ArrayType)sto.getType()).getNext());
            v.setIsModifiable(true);
            v.setIsAddressable(true);

            //Write Assembly: array usage
            v.setOffset(String.valueOf(++offsetCnt * -4));
            v.setBase("%fp");
            v.setArrayTag(true);
            codegen.DoArrayCheck(sto, expr, v);
            return v;
        }
        else if(sto.getType() instanceof PointerType) {
            VarSTO v = new VarSTO(sto.getName(), ((PointerType)sto.getType()).getNext());
            v.setIsModifiable(true);
            v.setIsAddressable(true);

            //Write Assembly: array of pointer usage
            v.setOffset(String.valueOf(++offsetCnt * -4));
            v.setBase("%fp");
            v.setIsPointer(true);

            codegen.DoArrayCheck(sto, expr, v);
            return v;


        }
       
        return sto;
        
        
    }

	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	STO DoDesignator3_ID(String strID)
	{
		STO sto;
        sto = new VarSTO("temp");
        //change accesslocal to access might break things
        if (isInStruct) {


            if(isInLoop ==0){
        	   if ((sto = m_symtab.accessLocal(strID)) == null ) {	
           
        	       if((sto = m_symtab.accessGlobal(strID)) == null){    
        	          m_nNumErrors++;
        	          m_errors.print(Formatter.toString(ErrorMsg.undeclared_id, strID));
        	          sto = new ErrorSTO(strID);
        	          return sto;
        	            
        	       }
        	   }
               
            }
            else {
        	    if ((sto = m_symtab.access(strID)) == null ) {  
        	       m_nNumErrors++;
        	       m_errors.print(Formatter.toString(ErrorMsg.undeclared_id, strID));
        	       sto = new ErrorSTO(strID);
        	       return sto;
        	    }


            
            
           }

        }
        else {
        	if ((sto = m_symtab.access(strID)) == null ) {
                 
        	    m_nNumErrors++;
        	    m_errors.print(Formatter.toString(ErrorMsg.undeclared_id, strID));
        	    sto = new ErrorSTO(strID);
        	    return sto;
        	}
        }
        return sto;
              
	}

    STO DoDes3_GlobalID(String strID)
    {
        STO sto;
        if (((sto = m_symtab.accessGlobal(strID)) == null)) 
		{
            
            m_nNumErrors++;
			m_errors.print(Formatter.toString(ErrorMsg.error0g_Scope, strID));
			sto = new ErrorSTO(strID);
            return sto;
                
		}
        return sto;

    }


	//----------------------------------------------------------------
	//
	//----------------------------------------------------------------
	Type DoStructType_ID(String strID)
	{
		STO sto;

    // change access to accessGlobal 
		if (m_symtab.accessGlobal(strID) == null)
		{

            if(!strID.equals(StructName)){ 
			    m_nNumErrors++; 
		 	    m_errors.print(Formatter.toString(ErrorMsg.undeclared_id, strID));
			    return new ErrorType();
            }
            else{
                return new StructType(strID);
            }
		}
        else{
            sto = m_symtab.access(strID);  // changed from access

		    if (!sto.isStructdef())
		    {
			   m_nNumErrors++;
			   m_errors.print(Formatter.toString(ErrorMsg.not_type, sto.getName()));
			   return new ErrorType();
		    }

        }
		return sto.getType();
        
	}

    STO DoBinaryExpr(STO a, Operator o, STO b) {

        if (a instanceof ErrorSTO) {
            return a;
        }
        else if (b instanceof ErrorSTO) {
            return b;
        }
        

        STO result = o.checkOperands(a, b);
        if ((result instanceof ErrorSTO)) {

            m_nNumErrors++;
            if(o.getOp().equals("%")) {
                if(result.getName() == "Mod-by-zero"){
                     m_nNumErrors++;
                     m_errors.print(ErrorMsg.error8_Arithmetic);
                     return result;
                     
                 }

                 m_errors.print(Formatter.toString(ErrorMsg.error1w_Expr,result.getName(),o.getOp(),"int"));                
            }

            else if(o.getOp().equals("+") || o.getOp().equals("-") || o.getOp().equals("/") || o.getOp().equals("*")) {
                 
                if((o.getOp().equals("/")) && (result.getName() == "Divide-by-zero")){
                     m_nNumErrors++;
                     m_errors.print(ErrorMsg.error8_Arithmetic);
                     return result;
                     
                }
                m_errors.print(Formatter.toString(ErrorMsg.error1n_Expr,result.getName(),o.getOp()));                
            }

            else if(o.getOp().equals("<") || o.getOp().equals("<=") || o.getOp().equals(">") || o.getOp().equals(">=")) {
                 m_errors.print(Formatter.toString(ErrorMsg.error1n_Expr,result.getName(),o.getOp())); 
            }
            else if(o.getOp().equals("==") || o.getOp().equals("!=")) {
                
                if(a.getType() instanceof PointerType && !(b.getType() instanceof PointerType)) {
                   m_errors.print(Formatter.toString(ErrorMsg.error17_Expr,o.getOp(),a.getType().getName(),b.getType().getName())); 
                }
                else if (!(a.getType() instanceof PointerType) && (b.getType() instanceof PointerType)) {
                    m_errors.print(Formatter.toString(ErrorMsg.error17_Expr,o.getOp(),a.getType().getName(),b.getType().getName()));                 
                }
                else {
                	if(a.getType() instanceof PointerType && b.getType() instanceof PointerType) {
                		if(!a.getType().isEquivalent(b.getType())) {
                			m_errors.print(Formatter.toString(ErrorMsg.error17_Expr,o.getOp(),a.getType().getName(),b.getType().getName()));                 
                		}
                	}
                	else {
                		m_errors.print(Formatter.toString(ErrorMsg.error1b_Expr,a.getType().getName(),o.getOp(),b.getType().getName())); 
                	}
                }

            }
            else if(o.getOp().equals("&&") || o.getOp().equals("||")) {
                m_errors.print(Formatter.toString(ErrorMsg.error1w_Expr,result.getName(),o.getOp(),"bool")); 
            }
            else if(o.getOp().equals("&") || o.getOp().equals("^") || o.getOp().equals("|")) {
                m_errors.print(Formatter.toString(ErrorMsg.error1w_Expr,result.getName(),o.getOp(),"int"));
            }

            result = new ErrorSTO("Error");
            return result;
            
        }
        result.setIsModifiable(false);
        result.setIsAddressable(false);

        //Assembly writing: Cover all binary arithmetric for int  check: 1.4
        
        // check if both operands are Lit 
        //boolean aIsLit = false;
        //boolean bIsLit = false;
        //if(a instanceof ConstSTO && !((ConstSTO)a).getLitTag()){
        //    aIsLit = true;
        //}

        //if(b instanceof ConstSTO && !((ConstSTO)b).getLitTag()){
        //     bIsLit = true;
        //}


            if((!(a instanceof ConstSTO)) || (!(b instanceof ConstSTO))){
                
                if(this.GetSavedLineCnt() == 0){
                    this.SaveLineCnt();
                }
                if(this.GetSavedLineCnt() != this.GetLineNum()){
                    if(codegen.getholdOff()){
                        codegen.TimeToWrite();
                    }

                    this.SaveLineCnt();
                    codegen.setholdOff(false);
                }
                else{
                    codegen.setholdOff(true);
                }
            
                   
                result.setBase("%fp");
                offsetCnt++;
                int val = -offsetCnt * result.getType().getSize();
                String value = String.valueOf(val);
                result.setOffset(value);

                // pass along tags
            
                //result.setArrayTag(a.getArrayTag());
                //result.setStructTag(a.getStructTag());
                //result.setIsPointer(a.getIsPointer());
                //result.flag = a.flag;

                // float case
                if(a.getType() instanceof FloatType || b.getType() instanceof FloatType){
                    
                    //type promotion
                    if(a.getType() instanceof IntType){
                       STO promote = new ExprSTO("temp");
                       offsetCnt ++;
                       int off = -offsetCnt * 4;
                       promote.setOffset(String.valueOf(off));
                       promote.setBase("%fp");

                       codegen.DoBinaryFloat(a, promote, b, null, o.getOp(), result);

                    }
                    else if(b.getType() instanceof IntType){
                       STO promote = new ExprSTO("temp");
                       offsetCnt ++;
                       int off = -offsetCnt * 4;
                       promote.setOffset(String.valueOf(off));
                       promote.setBase("%fp"); 

                       codegen.DoBinaryFloat(a, null, b, promote, o.getOp(), result);

                    }
                    //float to float
                    else{
                        codegen.DoBinaryFloat(a, null, b, null, o.getOp(), result);
                    }
                }
                // int to int
                else if (a.getType() instanceof IntType){
                    codegen.DoBinaryInt(a, b, o.getOp(), result);
                }
                // bool to bool
                else if(a.getType() instanceof BoolType && o.getOp().equals("==")){
  
                    codegen.DoBinaryInt(a, b, o.getOp(), result);
                }
                else if(a.getType() instanceof BoolType && o.getOp().equals("!=")){
 
                    codegen.DoBinaryInt(a, b, o.getOp(), result);
                }
                else if(a.getType() instanceof PointerType){
                    codegen.DoBinaryInt(a, b, o.getOp(), result);
                }

            }

        
        return result;
    }


    // This handles the LHS of short circuit for bool op
    void DoLHS(STO sto, String op){

        codegen.TimeToWrite();
        codegen.DoBinaryBoolLHS(sto, op);
        

    }
    // This handles the RHS of short circuit for bool op
    void DoRHS(STO a, STO b, String op, STO result){

       codegen.TimeToWrite();
       codegen.DoBinaryBoolRHS(a, b, op, result);


    }

    // This empty the buffer after finishing all binary/uniary ops
    void DoBoolWrite(){

        codegen.TimeToWrite();
        codegen.setholdOff(false);

    }

    STO DoUnaryExpr(STO a, Operator o) {
        
        if(a instanceof ErrorSTO) {
            return a;
        }

        STO result = o.checkOperands(a);
        if (result instanceof ErrorSTO) {

            
            if(o.getOp().equals("!")) {
                m_errors.print(Formatter.toString(ErrorMsg.error1u_Expr,result.getName(),o.getOp(),"bool")); 
            }
            m_nNumErrors++;
            result = new ErrorSTO("Error");
            
        }

        // Assembly Writing: This handles all unary ops writing to assembly

        offsetCnt ++;
        int val = -offsetCnt * 4;
        result.setOffset(String.valueOf(val));
        result.setBase("%fp");
   
        if(!(a instanceof ConstSTO)){
            if(this.GetSavedLineCnt() == 0){
                this.SaveLineCnt(); 
            }
            if(this.GetSavedLineCnt() != this.GetLineNum()){
        
                if(codegen.getholdOff()){
                    codegen.TimeToWrite();
                }
                this.SaveLineCnt();
                codegen.setholdOff(false);
            }
            else{
                codegen.setholdOff(true);

            }
            codegen.DoUnary(a, result, "%o0", o.getOp());
        }
        return result;
    
    }

    STO preDoIncDecCheck(String s1, STO a) {
        STO result;

        if (a instanceof ErrorSTO) {
            return a;
        }


        if (!(a.getType() instanceof NumericType) && !(a.getType() instanceof PointerType)) {
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error2_Type,a.getType().getName(), s1));
            return result = new ErrorSTO("Error");
        }
        else if (!(a.isModLValue())){
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error2_Lval,s1));
            result = new ErrorSTO("Error");
            return result;
        }
         
        if(s1.equals("++")){
            result = new ExprSTO("++" +a.getName(), a.getType());
        }
        else{
            result = new ExprSTO("--" +a.getName(), a.getType());
        }
        // Write Assembly: covers pre ++/-- only
        // set offset and base

        result.setBase("%fp");

        offsetCnt++;
        int val = -offsetCnt * result.getType().getSize();
        result.setOffset(String.valueOf(val));

        if(result.getType() instanceof IntType){
            STO b = new ConstSTO("1", new IntType("int"), 1);
            codegen.DoPrePostInt(a, b, s1, result, "%o2");
        }
        else if(result.getType() instanceof FloatType){
            STO b = new ConstSTO("1", new IntType("int"), 1);
            codegen.DoPrePostFloat(a, b, s1, result, "%f2");
        }
        else{
            STO b = new ConstSTO("4", new IntType("int"), 4);
            codegen.DoPrePostInt(a, b, s1, result, "%o2");

        }

        result.setIsAddressable(false);
        result.setIsModifiable(false);

       return result;

    }


    STO postDoIncDecCheck(String s1, STO a) {
        STO result;

        if (a instanceof ErrorSTO) {
            return a;
        }


        if (!(a.getType() instanceof NumericType) && !(a.getType() instanceof PointerType)) {
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error2_Type,a.getType().getName(), s1));
            return result = new ErrorSTO("Error");
        }
        else if (!(a.isModLValue())){
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error2_Lval,s1));
            result = new ErrorSTO("Error");
            return result;
        }
         
        if(s1.equals("++")){
            result = new ExprSTO(a.getName()+"++", a.getType());
        }
        else{
            result = new ExprSTO(a.getName()+"--", a.getType());
        }       

        // Write Assembly: covers post ++/-- only
        // set offset and base

        result.setBase("%fp");

        offsetCnt++;
        int val = -offsetCnt * result.getType().getSize();
        result.setOffset(String.valueOf(val));

        if(result.getType() instanceof IntType){
            STO b = new ConstSTO("1", new IntType("int"), 1);
            codegen.DoPrePostInt(a, b, s1, result, "%o0");
        }
        else if(result.getType() instanceof FloatType){

            STO b = new ConstSTO("1", new IntType("int"), 1);
            codegen.DoPrePostFloat(a, b, s1, result, "%f0");
        }
        else{
            STO b = new ConstSTO("4", new IntType("int"), 4);
            codegen.DoPrePostInt(a, b, s1, result, "%o0");

        }


        result.setIsAddressable(false);
        result.setIsModifiable(false);

       return result;

    }


    STO DoIf(STO a){
        STO result;

        if(a instanceof ErrorSTO) {
            return a;
        }


        if(!(a.getType().isEquivalent(new BoolType("Bool")))){
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error4_Test, a.getType().getName()));
            return new ErrorSTO("Error");
        }
        result = a;

        //Write Assembly: cover if statement 
        if(a instanceof ConstSTO && !((ConstSTO)a).getLitTag()){
            codegen.DoIfLitCond(a);
        }
        else{
            codegen.DoIfExprCond(a);
        }
        return result;
    }

    STO DoWhile(STO a){
        STO result;

        if(a instanceof ErrorSTO) {
            return a;
        }


        if(!(a.getType().isEquivalent(new BoolType("Bool")))){
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error4_Test, a.getType().getName()));
            return new ErrorSTO("Error");
        }
        result = a;

        //Write Assembly: cover while statement 
        //codegen.setholdOff(false);
        //codegen.DoWhileOpenLoop();
        //codegen.setholdOff(true);

        //codegen.TimeToWrite();
        
        if(a instanceof ConstSTO && !((ConstSTO)a).getLitTag()){
            codegen.DoWhileLitCond(a);
        }
        else{
            codegen.DoWhileExprCond(a);
        }
        return result;
    }
    // Write Assembly: call DoWhileOpenLoop in ACG
    void CallDoWhileOpenLoop(){
        codegen.DoWhileOpenLoop();
    }

    // Write Assembly: call DoWhileCloseLoop in ACG
    void CallDoWhileCloseLoop(){
        codegen.DoWhileCloseLoop();
    }

    // Write Assembly: call DoElse in ACG
    void CallDoElse(){
        codegen.DoElse();
    }
    // Write Assembly: call DoEndIf in ACG
    void CallDoIfEnd(){
        codegen.DoEndIf();
    }

    STO ProcessParams(String s) {
        String[] splitStr;
        String type;
        String id;
        String arr = "";
        String numArr = "";
        int dim = 0;
        Boolean isArray = false;
        Vector<Integer> sizes = new Vector<Integer>();

        
        if(s.contains("&")) {
            splitStr = s.split("&");
            type = splitStr[0].trim();
            id = "&" +splitStr[1].trim();
  
             
             if (id.contains("[") || id.contains("]")) {

                 
                 
               isArray = true;
               String[] splits = id.split(" ");;
               String newId = splits[0];
               arr = splits[1];
               numArr = splits[1];
               arr = splits[1].replaceAll("[^-?0-9]+", " ");
               String[] splitStrs;
               splitStrs = arr.trim().split(" ");
               for(int index = 0 ; index<splitStrs.length ; index++) {
                 sizes.add(Integer.parseInt(splitStrs[index]));
               }
               dim = sizes.size();
               id = newId;
 
             }
        
        }

    
        else if (s.contains("[") || s.contains("]")) {
               isArray = true;
               String[] split = s.split(" ");
               type = split[0];
               id = split[1];
               numArr = split[2];
               arr = split[2].replaceAll("[^-?0-9]+", " ");
               splitStr = arr.trim().split(" ");
         
               for(int index = 0 ; index<splitStr.length ; index++) {
                 sizes.add(Integer.parseInt(splitStr[index]));
               }
               dim = sizes.size();
 
        }

        else {
            splitStr = s.split("\\s+");
            type = splitStr[0].trim();
            id = splitStr[1].trim();

        }



        Type t;
        if(type.contains("*")) { // check for pointer
          int numPtr; 
          numPtr = this.CountChar(type,'*');
          String ptrStr = type.replace("*","");
          Vector<STO> ptrs = new Vector<STO>();
          for (int i =0; i <numPtr; i++) {
             ptrs.addElement(new ExprSTO("star"));
          }
          Type ptrType;
          switch (ptrStr) 
            {
               case "int" :  ptrType = new IntType("int");
                             break;
               case "float": ptrType = new FloatType("float");
                             break;
               case "bool":  ptrType = new BoolType("bool");
                             break;
               default:      ptrType = new StructType(ptrStr);
            }
            if(ptrType instanceof StructType) { // add to fix Arrow check
            	STO struct;
            	if(isInStruct) {
            		struct = m_symtab.getStruct();
            	}
            	else {
                 	struct = m_symtab.accessGlobal(ptrStr);
            	}

            	((StructType)ptrType).setScope(((StructType)struct.getType()).getScope());
            }
            t = this.DoPointer(ptrType,ptrs);
        }
        else { // non pointers
           switch (type) 
            {
               case "int" :  t = new IntType("int");
                             break;
               case "float": t = new FloatType("float");
                             break;
               case "bool":  t = new BoolType("bool");
                             break;
               default:      t = new StructType(type);
        
            }
            if(t instanceof StructType) { // added to fix arrow check
        		STO struct;
        		if(isInStruct) {
        			struct = m_symtab.getStruct();
        		}
        		else {
        	     	struct = m_symtab.accessGlobal(type);
        		}
            	((StructType)t).setScope(((StructType)struct.getType()).getScope());
            }
        
        }

        STO result;
        if(isArray == false){
            result = new VarSTO(id,t);
        }
        else{

            String name = type.concat("" + numArr);
            ArrayType aTopType = new ArrayType(name,sizes.get(0), dim,sizes.get(0));

            
            for(int i = 1; i <=dim; i++){
            
                if(i == dim){
                    aTopType.addNext(t);
                }
                else{  
                  int size = sizes.get(i);
                  String n = name.substring(0,name.lastIndexOf("["));
                  ArrayType typ = new ArrayType(n, size,dim-i,size);
                  aTopType.addNext(typ);
                }
                
                
                
            }
            result = new VarSTO(id,aTopType);
        }

        if (result.getName().contains("&")) {

            result.setName(result.getName().substring(1));
            result.flag = true;
        }
        if( result.getType() instanceof ArrayType ) {
            result.flag = true;
        }

        m_symtab.insert(result);
        return result;

    }





   
    STO DoReturnStmt(STO expr){
        

        if(expr instanceof ErrorSTO)
            return expr;
        if(!(m_symtab.getFunc().getReturnType() instanceof VoidType)){
    
            if(m_symtab.getFunc().flag == false)
            {
                if(!(expr.getType().isAssignable(m_symtab.getFunc().getReturnType()))){ 
                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error6a_Return_type, expr.getType().getName(), m_symtab.getFunc().getReturnType().getName()));
                    return new ErrorSTO("Error");
                }
            }
            else if(m_symtab.getFunc().flag == true){
                if(!(expr.getType().isEquivalent(m_symtab.getFunc().getReturnType()))){ 

                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error6b_Return_equiv, expr.getType().getName(), m_symtab.getFunc().getReturnType().getName()));
                    return new ErrorSTO("Error");
                }
                else if(!(expr.isModLValue())){
                    m_nNumErrors++;
                    m_errors.print(ErrorMsg.error6b_Return_modlval);
                    return new ErrorSTO("Error");
                }

                
            }

        }
        else if (m_symtab.getFunc().getReturnType() instanceof VoidType){
                    m_nNumErrors++;
                    m_errors.print(Formatter.toString(ErrorMsg.error6a_Return_type, expr.getType().getName(), m_symtab.getFunc().getReturnType().getName()));
                    return new ErrorSTO("Error");
        }



        // Write Assembly: for Lit return stmt
        if(expr instanceof ConstSTO && !((ConstSTO)expr).getLitTag()){
            STO promote = null;
            Type t = m_symtab.getFunc().getReturnType(); 
            String exp = "";
            if(t instanceof IntType){
                int i = ((ConstSTO)expr).getIntValue();
                exp = String.valueOf(i);
            }
            else if(t instanceof FloatType){
                // Type promotion
                if(expr.getType() instanceof IntType){
                    int i = ((ConstSTO)expr).getIntValue();
                    exp = String.valueOf(i);
                    promote = new ExprSTO("promote");
                    promote.setOffset(String.valueOf(++offsetCnt * -4));
                    promote.setBase("%fp");
                    
                }
                else{
                    float i = ((ConstSTO)expr).getFloatValue();
                    exp = String.valueOf(i);
                }

            }
            else if(t instanceof BoolType){
                int i = ((ConstSTO)expr).getBoolValue() ? 1 : 0;
                exp = String.valueOf(i);
            }

            
            codegen.DoReturnLit(m_symtab.getFunc(), exp, expr, promote);
            
            
        }
        // for all other cases
        else{
            // Type promotion
            STO promote = null;
            Type typ = m_symtab.getFunc().getReturnType();
            if(typ instanceof FloatType && expr.getType() instanceof IntType){
                promote = new ExprSTO("promote");
                promote.setOffset(String.valueOf(++offsetCnt * -4));
                promote.setBase("%fp");
            }
          
            codegen.DoReturnNonVoid(m_symtab.getFunc(), expr, promote);
        }
        //STO result = new ExprSTO("return", m_symtab.getFunc().getReturnType() );
        return expr;
    }


    STO DoReturnStmt(){
        if(!(m_symtab.getFunc().getReturnType() instanceof VoidType)){
                m_nNumErrors++;
                m_errors.print(ErrorMsg.error6a_Return_expr);
                return new ErrorSTO("Error");
        }
        
        
        //Write Assembly: for void return stmt
        codegen.DoReturnVoid(m_symtab.getFunc());

        return new ExprSTO("return", m_symtab.getFunc().getReturnType() );
    
    }

    STO MissingReturnStmt(Type typ, Vector<STO> stmtlist){
        int returncnt = 0;



        for(int i = 0; i < stmtlist.size(); i++){
           
            if(stmtlist.get(i) == null) {
                returncnt++;
                continue;
            }
            else if(stmtlist.get(i).getName().equals("return")){
                return stmtlist.get(i);
            }
           // else{
              //  returncnt++;
            //}
        }
        if(returncnt == stmtlist.size()){
            if (!(typ instanceof VoidType) ) {

               m_nNumErrors++;
               m_errors.print(ErrorMsg.error6c_Return_missing);
               return new ErrorSTO("Error");

            }
    
        }
        return new ExprSTO("return", typ);
    }



    STO DoExit(STO expr) {

        if(expr instanceof ErrorSTO) {
            return expr;
        }


        if(!(expr.getType().isAssignable(new IntType("int") ))) {
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error7_Exit,expr.getType().getName()));
            return new ErrorSTO("error check 7");
        }
        else {

            // Assembly writing : the lit case
            if(expr instanceof ConstSTO && !((ConstSTO)expr).getLitTag()){
                codegen.DoExitLit(expr);
            }
            // General case
            else{

                codegen.DoExitBase(expr);
            }
            return expr;
        }
    
    }

    STO DoUnarySign(String s, STO des){
        if(des instanceof ErrorSTO)
            return des;


        STO sto = new ExprSTO(des.getName(),des.getType());
        if(s == "-"){
            if( des instanceof ConstSTO){
                if(des.getType() instanceof IntType){
                    return new ConstSTO("-" + des.getName(), des.getType(), -1*((ConstSTO)des).getIntValue());
                }
                else if(des.getType() instanceof FloatType){
                    return new ConstSTO("-" + des.getName(), des.getType(), -1.0*((ConstSTO)des).getFloatValue());
                }
            }
            
        }
        else{
            if( des instanceof ConstSTO){
                if(des.getType() instanceof IntType){
                    return new ConstSTO("+" + des.getName(), des.getType(), ((ConstSTO)des).getIntValue());
                }
                else if(des.getType() instanceof FloatType){
                    return new ConstSTO("+" + des.getName(), des.getType(), ((ConstSTO)des).getFloatValue());
                }
            }
            
        }
        offsetCnt ++;
        int val = -offsetCnt * 4;
        sto.setOffset(String.valueOf(val));
        sto.setBase("%fp");

        String reg;
        if(des.getType() instanceof IntType){
            reg = "%o0"; 
        }
        else{
            reg = "%f0";
        }
   
        if(this.GetSavedLineCnt() == 0){
            this.SaveLineCnt(); 
        }
        if(this.GetSavedLineCnt() != this.GetLineNum()){
        
            if(codegen.getholdOff()){
                codegen.TimeToWrite();
            }
            this.SaveLineCnt();
            codegen.setholdOff(false);
        }
        else{
            codegen.setholdOff(true);
        }
        codegen.DoUnary(des, sto, reg, s);

        return sto;
    }

    public int CountChar(String s, char c) {
        int count = 0;
        for(char ch : s.toCharArray()) {
            if (ch == c ) {
                count++;
            } 
        }
        return count;
    }

    
    void DoInLoop(){
        isInLoop += 1;
    }

    void DoExitLoop(){
        isInLoop -= 1;
    }

    void DoBreak(){
        if(isInLoop <= 0){
            m_nNumErrors++;
            m_errors.print(ErrorMsg.error12_Break);
            return;
        }
        //Write Assembly: handles break statement in assembly
        codegen.DoBreak();
    }

    void DoContinue(){
         if(isInLoop <= 0){
            m_nNumErrors++;
            m_errors.print(ErrorMsg.error12_Continue);
            return;
        }
        //Write Assembly: handles continue statement in assembly
        codegen.DoContinue();

    }

    StructdefSTO getStructSTO() {
        return m_symtab.getStruct();
    }


    STO DoNullPointerCheck(STO sto){
        if(sto.getType() instanceof NullPointerType){
            m_nNumErrors++;
            m_errors.print(ErrorMsg.error15_Nullptr);
            return new ErrorSTO("Error");

        }
        return sto;
    }
    // this is only for * 
    STO DoPointerCheck(STO sto){
        if (sto instanceof ErrorSTO){
            return sto;
        }
        if(!(sto.getType() instanceof PointerType)){ // Pointer
            m_nNumErrors++;
            m_errors.print(Formatter.toString(ErrorMsg.error15_Receiver, sto.getType().getName()));
            return new ErrorSTO("Error");

        }
         // add 11/28
        return sto;

    }
    STO DoPointerArrowCheck(STO sto, String strID){
        if (sto instanceof ErrorSTO)
            return sto; 


        if(!(sto.getType() instanceof PointerType)){ // Arrow
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error15_ReceiverArrow, sto.getType().getName()));
           return new ErrorSTO("Error");

        }
        else if(!(((PointerType)sto.getType()).getBaseType() instanceof StructType)){
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error15_ReceiverArrow, sto.getType().getName()));
           return new ErrorSTO("Error");

        }
        else {
        
           Scope s = ((StructType)((PointerType)sto.getType()).getBaseType()).getScope();
           Vector<STO> locals = s.getLocals();
           for (int i = 0;i < locals.size() ; i++) {
           	   if(locals.get(i).getName().equals(strID)) {

                   STO temp;
                   if(locals.get(i) instanceof FuncSTO) {
                       temp = new FuncSTO(locals.get(i).getName(), locals.get(i).getType());
                       ((FuncSTO)temp).setReturnType(locals.get(i).getType());
                       ((FuncSTO)temp).setParams(((FuncSTO)locals.get(i)).getParams());
                       temp.flag = locals.get(i).flag;
                       temp.setOffset(String.valueOf(++offsetCnt * -4));
                       temp.setBase("%fp");
                       temp.setIsPointer(true);


                       codegen.DoDereference(sto, temp);

                       this.setStructFunCall(true);
                       codegen.DoCtorThis(temp);
           	           return temp;

                   }
                  
                  temp = new VarSTO(locals.get(i).getName(), locals.get(i).getType()); 
                  
                  //temp.setStructTag(locals.get(i).getStructTag());
                  temp.setStructOffset(locals.get(i).getStructOffset()); 
                  offsetCnt++;
                  temp.setOffset(String.valueOf(offsetCnt * -4));
                  temp.setBase("%fp");
                  temp.setIsPointer(true);
                 // if(((PointerType)sto.getType()).getBaseType() instanceof StructType){
                   //   sto.setStructTag(true);
                 // }
                  codegen.DoDereference(sto, temp);
                  STO result = new VarSTO(temp.getName(), temp.getType());
                  result.setStructOffset(temp.getStructOffset());
                  offsetCnt++;
                  result.setOffset(String.valueOf(offsetCnt * -4));
                  result.setBase("%fp");
                  result.setIsPointer(true);
                  codegen.DoStructCall(temp, result);
           	      return result;
           	   }
           	
           }
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error14f_StructExp,strID, ((PointerType)sto.getType()).getBaseType().getName()));
           return new ErrorSTO("error");

        }
        
    }

    // decl a pointer
    Type DoPointer(Type t, Vector<STO> ptrlist){
    
        PointerType TopType = new PointerType(t.getName() + this.PrintStar(ptrlist.size()));
        TopType.setNumPointers(ptrlist.size());
        if(ptrlist.isEmpty()){
            return t;
        }
        int numPtr = ptrlist.size();
        for (int i = 1; i <=numPtr ; i++){
            if(i == numPtr){
                TopType.addNext(t);
            }
            else{
                PointerType typ = new PointerType(t.getName()+ this.PrintStar(numPtr-i));
                typ.setNumPointers(numPtr-i);
                TopType.addNext(typ);
            }
        }
        
        return TopType;
    }


    STO DoDereference(STO sto){
        if(sto instanceof ErrorSTO){
            return sto;
        }
        // added 11/28
        STO result;
        
        result = new VarSTO(sto.getName(),((PointerType)sto.getType()).getNext());

        if(!(((PointerType)sto.getType()).getNext() instanceof ArrayType)){
           result.setIsModifiable(true);
           result.setIsAddressable(true);
        }
        else{
           result.setIsModifiable(false);
           result.setIsAddressable(true);

        }

        result.setIsPointer(true);
        result.setOffset(String.valueOf(++offsetCnt * -4));
        result.setBase("%fp");
        codegen.DoDereference(sto, result);
        result.flag = sto.flag;
        result.setArrayTag(sto.getArrayTag());
        return result;
    }


   Type CreateArrayType(Type base, Vector<STO> arraylist) {
       //Type typ;
       int dim = arraylist.size();
       int size = 0;
       for(int i = 0; i < dim; i++){
         STO num = arraylist.get(i);
         if(!(num.getType() instanceof IntType) || !(num instanceof ConstSTO)) {
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error10i_Array, num.getType().getName()));
           return new ErrorType();
         }
       }
       int len = ((ConstSTO)arraylist.get(0)).getIntValue();
       String s = base.getName().concat("[" + ((ConstSTO)arraylist.get(0)).getIntValue()+ "]");
       size = ((ConstSTO)arraylist.get(0)).getIntValue();
       ArrayType TopArray = new ArrayType(s, size, dim,len);
       for (int i = 1; i <= dim; i++) {
          if(i == dim){
            TopArray.addNext(base);
          }
          else{
            STO num = arraylist.get(i);
            int number = ((ConstSTO)num).getIntValue();
            size += number;
            s = s.concat("[" + number + "]");
            ArrayType typ = new ArrayType(s, size, dim-i,number);
            TopArray.addNext(typ);
          }
       }
       size = size * 4;
       return TopArray;
   }


    String PrintStar(int i){
        return new String(new char[i]).replace("\0", "*");
    }

    STO DoNew(STO sto, Vector<STO> params){
        if(sto instanceof ErrorSTO)
            return sto;

        if(!sto.isModLValue()){
           m_nNumErrors++;
           m_errors.print(ErrorMsg.error16_New_var);
           return new ErrorSTO("error");
        }
        else if(!(sto.getType() instanceof PointerType)){
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error16_New, sto.getType().getName()));
           return new ErrorSTO("error");           
        }
        this.setNewCall(true);
        if(sto.getType() instanceof PointerType) {
            if(params.size() == 0){
                if(!(((PointerType)sto.getType()).getNext() instanceof StructType)) {
                    
                    codegen.DoNew(sto);
                    return sto;
                }
                else if(((PointerType)sto.getType()).getNext() instanceof StructType){
                   codegen.DoNew(sto);
                   STO def = this.DoDereference(sto);
                   this.setInNew(def.getIsPointer());
                    // add 11/28 "new" 
                   this.DoCtorStructs("new "+def.getName(), def.getType(), new Vector<STO>() ,params, null);
        
                   //System.out.println("Def:" + def.getName());
                }
            }
            else{
                if(((PointerType)sto.getType()).getNext() instanceof StructType){
                   codegen.DoNew(sto);
                   STO def = this.DoDereference(sto);
                   this.setInNew(def.getIsPointer());
        
                   // add 11/28 "new" 
                   this.DoCtorStructs("new " +def.getName(), def.getType(), new Vector<STO>()  ,params, null);
        
                }
                else if(!(((PointerType)sto.getType()).getNext() instanceof StructType)) {
                   m_nNumErrors++;
                   m_errors.print(Formatter.toString(ErrorMsg.error16b_NonStructCtorCall, sto.getType().getName()));
                   return new ErrorSTO("error"); 
                }



            }


        }
        return sto;

    }

    STO DoDelete(STO sto){
        if(sto instanceof ErrorSTO)
            return sto;


        if(!sto.isModLValue()){
           m_nNumErrors++;
           m_errors.print(ErrorMsg.error16_Delete_var);
           return new ErrorSTO("error");
        }
        else if(!(sto.getType() instanceof PointerType)){
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error16_Delete, sto.getType().getName()));
           return new ErrorSTO("error");           
        }
        // add 11/30 below for pointer to struct case 
        this.setNewCall(true);
        if(sto.getType() instanceof PointerType) {
            
            if(!(((PointerType)sto.getType()).getNext() instanceof StructType)) {
                    
                codegen.DoDelete(sto);
                return sto;
            }
            else if(((PointerType)sto.getType()).getNext() instanceof StructType){
                STO def = this.DoDereference(sto);
                this.setInNew(def.getIsPointer());
                 
                this.DoCtorStructs("delete "+def.getName(), def.getType(), new Vector<STO>() , new Vector<STO>(), null);
                codegen.DoDelete(sto);
                
            }
            

        }
        return sto;
    }

    STO DoAddressOf(STO sto){
        if(sto instanceof ErrorSTO){
            return sto;
        }


        if(!sto.getIsAddressable()){
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error18_AddressOf, sto.getType().getName()));
           return new ErrorSTO("error"); 
        }
        PointerType ptr;
        if(sto.getType() instanceof PointerType) {
            ptr =  new PointerType(sto.getType().getName()+ this.PrintStar(1));
            ptr.setNumPointers(((PointerType)sto.getType()).getNumPtr()+1);
        }
        else {
            ptr =  new PointerType(sto.getType().getName()+ this.PrintStar(1),1);

        }
        ptr.addNext(sto.getType());


        ExprSTO expr = new ExprSTO("&"+sto.getName(),ptr);
        expr.setIsAddressable(false);
        expr.setIsAddressable(false);

       // if(this.GetSavedLineCnt() == 0){
       //     this.SaveLineCnt();
       // }
      //  if(this.GetSavedLineCnt() != this.GetLineNum()){
      //      if(codegen.getholdOff()){
      //          codegen.TimeToWrite();
      //      }
      //      this.SaveLineCnt();
      //      codegen.setholdOff(false);
     //   }
     //   else{
            codegen.setholdOff(true);
     //   }
      

        expr.setOffset(String.valueOf(++offsetCnt * -4));
        expr.setBase("%fp");
        codegen.DoAddress(sto, expr);
        return expr;
         
    }

    STO DoSizeOf(STO sto){
        if(sto instanceof ErrorSTO){
            return sto;
        }
        
        if(!(sto.getType() instanceof Type)){
           m_nNumErrors++;
           m_errors.print(ErrorMsg.error19_Sizeof);
           return new ErrorSTO("error"); 

        }
        if(!(sto.getIsAddressable())) {
           m_nNumErrors++;
           m_errors.print(ErrorMsg.error19_Sizeof);
           return new ErrorSTO("error");          
        }
        ConstSTO result;
 
        if(sto.getType() instanceof ArrayType){
          result = new ConstSTO(sto.getName(), new IntType("int"), ((ArrayType)sto.getType()).getTotalSize());

        }
        else {
          result = new ConstSTO(sto.getName(), new IntType("int"), sto.getType().getSize());
        
        }


        result.setIsAddressable(false);
        result.setIsModifiable(false);
        return result;       
    }





    STO DoSizeOf(Type t, Vector<STO> arraylist){
        if(!(t instanceof Type)){
           m_nNumErrors++;
           m_errors.print(ErrorMsg.error19_Sizeof);
           return new ErrorSTO("error"); 

        }
        ConstSTO result;

        if(!(arraylist.isEmpty())) {
            int numDim = arraylist.size();
            STO sizeStoTop = arraylist.elementAt(0);
            ArrayType aTopType = new ArrayType(t.getName(), ((ConstSTO)sizeStoTop).getIntValue(), numDim,((ConstSTO)sizeStoTop).getIntValue());

            
            for(int i = 1; i <=numDim; i++){
            
                if(i == numDim){
                    aTopType.addNext(t);
                }
                else{  
                  STO sizeSto = arraylist.elementAt(i);
                  int len = ((ConstSTO)sizeSto).getIntValue();
                  ArrayType typ = new ArrayType(t.getName()+"["+ ((ConstSTO)sizeSto).getIntValue() +"]", ((ConstSTO)sizeSto).getIntValue(),numDim-i,len);
                  aTopType.addNext(typ);

                }                        
            }
		    result = new ConstSTO(t.getName(), new IntType("int"),aTopType.getTotalSize());
        }
        else {
           result = new ConstSTO(t.getName(), new IntType("int"), t.getSize());

        }
      

        result.setIsAddressable(false);
        result.setIsModifiable(false);
        return result;       
    }




    String processArray(Vector<STO> v) {
        String s = "";
      for(int i = 0; i < v.size();i++) {
         s = s.concat("["+v.get(i).getName() + "]");
      }
      return s; 
    
    }

    STO DoTypeCast(Type t, STO sto) {
        if(sto instanceof ErrorSTO){
            return sto;
        }


        if (sto.getType() instanceof NullPointerType) {
             m_nNumErrors++;
             m_errors.print(Formatter.toString(ErrorMsg.error20_Cast,sto.getType().getName(),t.getName()));
             return new ErrorSTO("error"); 

        }

        codegen.setholdOff(true);

        if(sto.getType() instanceof BasicType || sto.getType() instanceof PointerType){

            STO res = new ConstSTO("temp");
            if(sto instanceof ConstSTO){

                Type typ = sto.getType();
                // Cast float/int to Bool
                if( typ instanceof BoolType && t instanceof NumericType){
                    int val=0;
                    if(((ConstSTO)sto).getBoolValue()){val = 1;}
                    else if (!(((ConstSTO)sto).getBoolValue())) {val = 0;}
                    res = new ConstSTO(sto.getName(), t, val);
                    
                }
                // Cast Bool to Int
               else if(typ instanceof IntType && t instanceof BoolType){
                    
                    res = new ConstSTO(sto.getName(), t, ((ConstSTO)sto).getIntValue());
               }
               // Cast Bool to float
               else if(typ instanceof FloatType && t instanceof BoolType){
                    if(((ConstSTO)sto).getFloatValue() == 0.0)
                        res = new ConstSTO(sto.getName(), t, 0.0);
                    else if(((ConstSTO)sto).getFloatValue() != 0.0)
                        res = new ConstSTO(sto.getName(), t, 1.0);
   
               }
               // Cast int to float
               else if(typ instanceof FloatType && t instanceof IntType){
                    float val = ((ConstSTO)sto).getFloatValue();
                    int intVal = (int)val;
                    res = new ConstSTO(sto.getName(), t,intVal);
               
               }
               // float to int
               else if(typ instanceof IntType && t instanceof FloatType){
                    float val = ((ConstSTO)sto).getFloatValue();
                    //float floatVal = (float)val;
                    res = new ConstSTO(sto.getName(), t, val);
               }
               else if(t instanceof PointerType && typ instanceof BasicType){
                    res = new ExprSTO(sto.getName(), t);
               }
               else {
                  return sto;
               }
            
            }
            else{
                res = new ExprSTO(sto.getName(), t);

                
            }
            if(sto instanceof ConstSTO){
                if(((ConstSTO)sto).getLitTag()) {
                   res.setOffset(String.valueOf(++offsetCnt  * -4));
                   res.setBase("%fp");
                }
            }
            else {
                res.setOffset(String.valueOf(++offsetCnt  * -4));
                res.setBase("%fp");

            }

            if( (t instanceof FloatType) && (sto.getType() instanceof IntType || sto.getType() instanceof BoolType)
                 || (t instanceof BoolType && sto.getType() instanceof FloatType)   ) {
                STO promote = new ExprSTO("promote");
                promote.setOffset(String.valueOf(++offsetCnt * -4));
                promote.setBase("%fp");
                codegen.DoTypeCast(sto,t,res,promote);

            }
            else {
                codegen.DoTypeCast(sto,t,res,null);
            }


            res.setIsAddressable(false);
            res.setIsModifiable(false);
            return res;

        }
        else{
           m_nNumErrors++;
           m_errors.print(Formatter.toString(ErrorMsg.error20_Cast, sto.getType().getName(), t.getName()));
           return new ErrorSTO("error"); 

        }

        //return sto;
        

    }


    //------------------------------------------------------------------------
    //cout for assembly 
    //-----------------------------------------------------------------------
    void DoPrint(STO sto){
        // This is here because we still want everything besides funccall to print immediately
        if(codegen.getholdOff()){
            codegen.TimeToWrite();
        }
        codegen.setholdOff(false);
        
            
        Type t = sto.getType();


        //check for endl
        if(sto instanceof ExprSTO){
            if(sto.getName().equals("endl")){
                codegen.printNL("%o0");

            }
            else{
                codegen.printVar(sto, "%l7");

            }
        }

        else if(sto instanceof ConstSTO && !((ConstSTO)sto).getLitTag()){
            // const string printing
            if(!(t instanceof BasicType)){
                codegen.printConstStr(sto, "%o1");
            }
            else if(t instanceof FloatType){
                codegen.printConstFloat(sto, "%f0");
            }
            else if(t instanceof IntType){
                codegen.printConstInt(sto, "%o1");
            }
            else{
                codegen.printConstBool(sto,"%o0");
            }
        }
        else{
            codegen.printVar(sto, "%l7");
        }
   
    }

    // cin for assembly
    void DoCin(STO sto){
    
        codegen.DoCin(sto);
    }



}
