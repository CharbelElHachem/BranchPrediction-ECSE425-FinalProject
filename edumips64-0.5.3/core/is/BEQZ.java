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
    BitSet64 bs;
    Register pc, b_pc;
    String pc_old, pc_new, offset;
    switch(cpu.getPredictionMode()) {
      case TAKEN:
        cpu.isPredictable = true;
        /* Do prediction. In this case we always assume taken.*/
        bs=new BitSet64();
        bs.writeHalf(params.get(OFFSET_FIELD));
        offset=bs.getBinString();
        pc=cpu.getPC();
        b_pc=cpu.getBPC();
        pc_old=cpu.getPC().getBinString();
        pc_new=InstructionsUtils.twosComplementSum(pc_old,offset);
        b_pc.setBits(pc_old, 0);
        pc.setBits(pc_new,0);
        logger.info(">> set PC to "+pc_new+"\n---------------------------------------------");
        break;
      case LOCAL:
        boolean predictIsTaken=cpu.getLocalPrediction(cpu.getPC().getBinString());
        if(predictIsTaken) {
          bs=new BitSet64();
          bs.writeHalf(params.get(OFFSET_FIELD));
          offset=bs.getBinString();
          pc=cpu.getPC();
          b_pc=cpu.getBPC();
          pc_old=cpu.getPC().getBinString();
          pc_new=InstructionsUtils.twosComplementSum(pc_old,offset);
          b_pc.setBits(pc_old, 0);
          pc.setBits(pc_new,0);
          logger.info("L>> set PC to "+pc_new+"\n---------------------------------------------");
        }
        break;
      case NOTTAKEN:
      default:
        break;
    }
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

    boolean predictIsTaken; // Was the prediction to take the branch?
    switch (cpu.getPredictionMode()) {
      case LOCAL:
        if (cpu.getBPC().getBinString().equals("0")) {
          predictIsTaken = cpu.getLocalPrediction(cpu.getPC().getBinString());
        } else {
          predictIsTaken = cpu.getLocalPrediction(cpu.getBPC().getBinString());
        }
        break;
      case TAKEN:
        predictIsTaken = true;
        break;
      case NOTTAKEN:
      default:
        predictIsTaken = false;
    }
		if(condition)             // The branch condition was true (i.e. the branch is to be taken)
		{
      // Update based on prediction
      if (predictIsTaken) {
        if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL) { // Update the BHT as necessary
          logger.info("L>> Branch correctly predicted taken, updating BHT");
          cpu.updateLocalPrediction(cpu.getBPC().getBinString(), true);
        } else { // Prediction was correct, but no table to update (i.e. CPU.PREDICTIONMode.TAKEN)
          logger.info("L>> Branch correctly predicted taken");
        }
      } else {  // Prediction was wrong, must go back
        String pc_new = "";
        Register pc = cpu.getPC();
        String pc_old = cpu.getPC().getBinString();

        // Subtract 4 from pc_old using safe methods
        BitSet64 bs_temp=new BitSet64();
        bs_temp.writeDoubleWord(-4);
        pc_old=InstructionsUtils.twosComplementSum(pc_old,bs_temp.getBinString());
        pc_new=InstructionsUtils.twosComplementSum(pc_old,offset);
        pc.setBits(pc_new,0);
        if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL) {
          logger.info("L>> Branch incorrectly predicted not taken, updating BHT");
          cpu.updateLocalPrediction(pc_old, true);
        } else {
          logger.info("L>> Branch incorrectly predicted not taken");
        }
        logger.info("Branched to " + pc_new + "\n---------------------------------------------");
        throw new MispredictTakenException();
      }
		} else {   // The condition is false and the branch is not to be taken
      if (predictIsTaken) { // The prediction was incorrect and we branched unnecessarily
        Register pc = cpu.getPC();
        String pc_b = cpu.getBPC().getBinString();
        pc.setBits(pc_b, 0);
        if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL) {
          cpu.updateLocalPrediction(pc_b, false);
          Register b_pc = cpu.getBPC();
          b_pc.setBits("0", 0);
          logger.info("L>> Branch not taken, incorrectly predicted taken, updating BHT");
        } else {
          logger.info("L>> Branch not taken, incorrectly predicted taken");
        }
        logger.info(">> Setting PC to " + pc_b + "\n---------------------------------------------");
        throw new MispredictTakenException();
      }
      else {  // Prediction was correct
        if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL) {
          cpu.updateLocalPrediction(cpu.getPC().getBinString(),false);
          logger.info("L>> Correctly predicted not taken, updating BHT");
        } else {
          logger.info("L>> Correctly predicted not taken");
        }
      }
		}
	}
      public void pack() throws IrregularStringOfBitsException {
	repr.setBits(OPCODE_VALUE, OPCODE_VALUE_INIT);
	repr.setBits(Converter.intToBin(RS_FIELD_LENGTH, 0/*params.get(RS_FIELD)*/), RS_FIELD_INIT);
	repr.setBits(Converter.intToBin(RT_FIELD_LENGTH,params.get(RS_FIELD)/* 0*/), RT_FIELD_INIT);
	repr.setBits(Converter.intToBin(OFFSET_FIELD_LENGTH, params.get(OFFSET_FIELD)/4), OFFSET_FIELD_INIT);
    }
}
