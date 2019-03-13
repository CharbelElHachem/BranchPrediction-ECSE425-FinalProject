/*
 * BEQZ.java
 *
 * may 2006
 * Instruction BEQZ of the MIPS64 Instruction Set
 * (c) 2006 EduMips64 project - Trubia Massimo, Russo Daniele
 *
 * This file is part of the EduMIPS64 project, and is released under the GNU
 * General Public License.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edumips64.core.is;

import edumips64.core.*;
import edumips64.utils.*;
import java.util.logging.Logger;


/** <pre>
 *        Syntax: BNQZ rs, offset
 *   Description: if rs == 0 then branch
 *                To test a GPR then do a PC-relative conditional branch
 *</pre>
  * @author Trubia Massimo, Russo Daniele
 */
public class BEQZ extends FlowControl_IType
{       
        protected final static int OFFSET_FIELD=1;
	public String OPCODE_VALUE = "000110";
	
	/** Creates a new instance of BEQZ */
	public BEQZ() 
	{
            super.OPCODE_VALUE = OPCODE_VALUE;
	    syntax="%R,%B";
            name ="BEQZ";
	}
	public void IF()
	throws TwosComplementSumException, IrregularStringOfBitsException, IrregularWriteOperationException
	{
		cpu.isPredictable=true;
		//do prediction
		//in this case we are doing always taken
		//lets update the pc to target offset
        //converting offset into a signed binary value of 64 bits in length
        BitSet64 bs=new BitSet64();
        bs.writeHalf(params.get(OFFSET_FIELD));
        String offset=bs.getBinString();
        Register pc=cpu.getPC();
        Register b_pc=cpu.getBPC();
        String pc_old=cpu.getPC().getBinString();
        String pc_new=InstructionsUtils.twosComplementSum(pc_old,offset);
        b_pc.setBits(pc_old, 0);
        pc.setBits(pc_new,0);
		logger.info(">> set PC to "+pc_new+"\n---------------------------------------------");

	}
	public void ID()
	throws MispredictTakenException, RAWException, IrregularWriteOperationException, IrregularStringOfBitsException, JumpException,TwosComplementSumException 	
	{
			//getting registers rs and rt
		 if(cpu.getRegister(params.get(RS_FIELD)).getWriteSemaphore() > 0)
			 throw new RAWException();
		String rs=cpu.getRegister(params.get(RS_FIELD)).getBinString();
		String zero = Converter.positiveIntToBin(64, 0);
                //converting offset into a signed binary value of 64 bits in length
                BitSet64 bs=new BitSet64();
                bs.writeHalf(params.get(OFFSET_FIELD));
                String offset=bs.getBinString();
		boolean condition=rs.equals(zero);
		if(condition)
		{
			//our prediction was correct nothing to do but update table if we had one
			if(false) {
				String pc_new="";
	            Register pc=cpu.getPC();
	            String pc_old=cpu.getPC().getBinString();
	    
	            //subtracting 4 to the pc_old temporary variable using bitset64 safe methods
	            BitSet64 bs_temp=new BitSet64();
	            bs_temp.writeDoubleWord(-4);
	            pc_old=InstructionsUtils.twosComplementSum(pc_old,bs_temp.getBinString());
	    
	            //updating program counter
	            pc_new=InstructionsUtils.twosComplementSum(pc_old,offset);
	            pc.setBits(pc_new,0);
				logger.info("Jumped to "+pc_new+"\n---------------------------------------------");
	            throw new MispredictTakenException(); 
			}
		    
            
		}else {
			//incorrect prediction, is not taken
	        Register pc=cpu.getPC();
	        String pc_b=cpu.getBPC().getBinString();
	        pc.setBits(pc_b,0);
			logger.info(">> miss was Not Taken set PC to "+pc_b+"\n---------------------------------------------");
            throw new MispredictTakenException(); //change is to mispredict nottaken
		}
	}
      public void pack() throws IrregularStringOfBitsException {
	repr.setBits(OPCODE_VALUE, OPCODE_VALUE_INIT);
	repr.setBits(Converter.intToBin(RS_FIELD_LENGTH, 0/*params.get(RS_FIELD)*/), RS_FIELD_INIT);
	repr.setBits(Converter.intToBin(RT_FIELD_LENGTH,params.get(RS_FIELD)/* 0*/), RT_FIELD_INIT);
	repr.setBits(Converter.intToBin(OFFSET_FIELD_LENGTH, params.get(OFFSET_FIELD)/4), OFFSET_FIELD_INIT); 
    }  
}
