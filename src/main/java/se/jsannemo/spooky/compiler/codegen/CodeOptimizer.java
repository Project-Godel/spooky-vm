package se.jsannemo.spooky.compiler.codegen;

import java.util.List;

import se.jsannemo.spooky.compiler.ir.*;
import se.jsannemo.spooky.compiler.ir.IrStatement.*;

/**
 * This class takes a unoptimized {@code IrProgram} and tries to remove all unnecessary
 * instructions.
 * 
 * @since 1.3
 * @author HardCoded	<https://github.com/Kariaro>
 */
public final class CodeOptimizer {
	private CodeOptimizer() {}
	
	/**
	 * Takes a {@code IrProgram} and tries to remove all unnecessary instructions.
	 * 
	 * @param program
	 * @return a optimized {@code IrProgram}
	 * @author HardCoded
	 */
	public static IrProgram optimize(IrProgram ir) {
		for(String key : ir.functions.keySet()) {
			IrFunction func = ir.functions.get(key);
			while(optimize_function(ir, func));
		}
		
		return ir;
	}
	
	private static boolean optimize_function(IrProgram ir, IrFunction func) {
		List<IrStatement> list = func.body;
		boolean changed = false;
		
		// note: If IrStore is used as a parameter of a function it should not be optimized.
		//       This is because IrStore or 'CONST' uses less bytes than 'MOV' would use.
		
		for(int i = 0; i < list.size() - 1; i++) {
			IrStatement stat = list.get(i);
			boolean result;
			
			if(stat instanceof IrStore store) {
				result = optimize_store(ir, list, store, i);
			} else if(stat instanceof IrCopy copy) {
				result = optimize_copy(ir, list, copy, i);
			} else {
				result = false;
			}
			
			// task: If an instruction is doing an operation on two data pointers we
			//       should instead compute the value and replace the instruction with
			//       a IrStore.
			// task: Sometimes values are stored some instructions above where they are
			//       used. Write an algorithm to optimize those instructions this.
			// task: When IrCopy('DT[a]', '[b]') is used we should replace it with a
			//       smaller instruction IrStore(number, '[b]').
			
			if(result) {
				changed = true;
				i -= (i > 0) ? 2:1;
			}
		}
		
		return changed;
	}
	
	private static boolean optimize_store(IrProgram ir, List<IrStatement> body, IrStore store, int index) {
		IrAddr source = store.addr();
		
		IrStatement next = body.get(index + 1);
		if(next instanceof IrMul mul) {
			boolean ra = source.equals(mul.a());
			boolean rb = source.equals(mul.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrMul.forTermsAndTarget(ra ? replace:mul.a(), rb ? replace:mul.b(), mul.result()));
				return true;
			}
			
		} else if(next instanceof IrDiv div) {
			boolean ra = source.equals(div.a());
			boolean rb = source.equals(div.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrDiv.forTermsAndTarget(ra ? replace:div.a(), rb ? replace:div.b(), div.result()));
				return true;
			}
			
		} else if(next instanceof IrLessEquals le) {
			boolean ra = source.equals(le.a());
			boolean rb = source.equals(le.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrLessEquals.forTermsAndTarget(ra ? replace:le.a(), rb ? replace:le.b(), le.result()));
				return true;
			}
		} else if(next instanceof IrLessThan lt) {
			boolean ra = source.equals(lt.a());
			boolean rb = source.equals(lt.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrLessThan.forTermsAndTarget(ra ? replace:lt.a(), rb ? replace:lt.b(), lt.result()));
				return true;
			}
		} else if(next instanceof IrAdd add) {
			boolean ra = source.equals(add.a());
			boolean rb = source.equals(add.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrAdd.forTermsAndTarget(ra ? replace:add.a(), rb ? replace:add.b(), add.result()));
				return true;
			}
		} else if(next instanceof IrSub sub) {
			boolean ra = source.equals(sub.a());
			boolean rb = source.equals(sub.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrSub.forTermsAndTarget(ra ? replace:sub.a(), rb ? replace:sub.b(), sub.result()));
				return true;
			}
		} else if(next instanceof IrEquals eq) {
			boolean ra = source.equals(eq.a());
			boolean rb = source.equals(eq.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrEquals.forTermsAndTarget(ra ? replace:eq.a(), rb ? replace:eq.b(), eq.result()));
				return true;
			}
		} else if(next instanceof IrMod mod) {
			boolean ra = source.equals(mod.a());
			boolean rb = source.equals(mod.b());
			
			if(ra || rb) {
				IrAddr replace = getNumber(ir, store.value());
				body.remove(index);
				body.set(index, IrMod.forTermsAndTarget(ra ? replace:mod.a(), rb ? replace:mod.b(), mod.result()));
				return true;
			}
		} else {
			
		}
		
		return false;
	}
	
	private static boolean optimize_copy(IrProgram ir, List<IrStatement> body, IrCopy copy, int index) {
		IrAddr source = copy.to();
		IrAddr replace = copy.from();
		
		IrStatement next = body.get(index + 1);
		if(next instanceof IrMul mul) {
			boolean ra = source.equals(mul.a());
			boolean rb = source.equals(mul.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrMul.forTermsAndTarget(ra ? replace:mul.a(), rb ? replace:mul.b(), mul.result()));
				return true;
			}
			
		} else if(next instanceof IrDiv div) {
			boolean ra = source.equals(div.a());
			boolean rb = source.equals(div.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrDiv.forTermsAndTarget(ra ? replace:div.a(), rb ? replace:div.b(), div.result()));
				return true;
			}
			
		} else if(next instanceof IrLessEquals le) {
			boolean ra = source.equals(le.a());
			boolean rb = source.equals(le.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrLessEquals.forTermsAndTarget(ra ? replace:le.a(), rb ? replace:le.b(), le.result()));
				return true;
			}
		} else if(next instanceof IrLessThan lt) {
			boolean ra = source.equals(lt.a());
			boolean rb = source.equals(lt.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrLessThan.forTermsAndTarget(ra ? replace:lt.a(), rb ? replace:lt.b(), lt.result()));
				return true;
			}
		} else if(next instanceof IrAdd add) {
			boolean ra = source.equals(add.a());
			boolean rb = source.equals(add.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrAdd.forTermsAndTarget(ra ? replace:add.a(), rb ? replace:add.b(), add.result()));
				return true;
			}
		} else if(next instanceof IrSub sub) {
			boolean ra = source.equals(sub.a());
			boolean rb = source.equals(sub.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrSub.forTermsAndTarget(ra ? replace:sub.a(), rb ? replace:sub.b(), sub.result()));
				return true;
			}
		} else if(next instanceof IrEquals eq) {
			boolean ra = source.equals(eq.a());
			boolean rb = source.equals(eq.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrEquals.forTermsAndTarget(ra ? replace:eq.a(), rb ? replace:eq.b(), eq.result()));
				return true;
			}
		} else if(next instanceof IrMod mod) {
			boolean ra = source.equals(mod.a());
			boolean rb = source.equals(mod.b());
			
			if(ra || rb) {
				body.remove(index);
				body.set(index, IrMod.forTermsAndTarget(ra ? replace:mod.a(), rb ? replace:mod.b(), mod.result()));
				return true;
			}
		} else {
			
		}
		
		return false;
	}
	
	private static IrAddr getNumber(IrProgram ir, int value) {
		int index = ir.data.indexOf(value);
		
		if(index < 0) {
			ir.data.add(value);
			return IrAddr.dataCell(ir.data.size() - 1);
		}
		
		return IrAddr.dataCell(index);
	}
}
