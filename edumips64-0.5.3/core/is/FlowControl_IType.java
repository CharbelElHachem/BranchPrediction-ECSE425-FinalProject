/*
 * FlowControl_IType.java
 *
 * 15th may 2006
 * Subgroup of the MIPS64 Instruction Set
 * (c) 2006 EduMips64 project - Trubia Massimo, Russo Daniele
 *
 * This file is part of the EduMIPS64 project, and is released under the GNU
 * General Public License.
 *
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

/** This is the base class for immediate flow control instructions
 *
 * @author Trubia Massimo, Russo Daniele
 */
public abstract class FlowControl_IType extends FlowControlInstructions {
    final static int RS_FIELD=0;
    final static int RT_FIELD=1;
    final static int OFFSET_FIELD=2;
    final static int RT_FIELD_INIT=11;
    final static int RS_FIELD_INIT=6;
    final static int OFFSET_FIELD_INIT=16;
    final static int RT_FIELD_LENGTH=5;
    final static int RS_FIELD_LENGTH=5;
    final static int OFFSET_FIELD_LENGTH=16;
    String OPCODE_VALUE="";
    final static int OPCODE_VALUE_INIT=0;
    public FlowControl_IType() {
	this.syntax="%R,%R,%E";
        this.paramCount=3;
    }

    public void ID() throws MispredictTakenException, RAWException, IrregularWriteOperationException, IrregularStringOfBitsException,JumpException,TwosComplementSumException {
    }

    public void EX() throws IrregularStringOfBitsException, IntegerOverflowException,IrregularWriteOperationException {
    }

    public void MEM() throws IrregularStringOfBitsException, MemoryElementNotFoundException {
    }

    public void WB() throws IrregularStringOfBitsException {
    }

    public void pack() throws IrregularStringOfBitsException {
	repr.setBits(OPCODE_VALUE, OPCODE_VALUE_INIT);
	repr.setBits(Converter.intToBin(RS_FIELD_LENGTH, params.get(RS_FIELD)), RS_FIELD_INIT);
	repr.setBits(Converter.intToBin(RT_FIELD_LENGTH, params.get(RT_FIELD)), RT_FIELD_INIT);
	repr.setBits(Converter.intToBin(OFFSET_FIELD_LENGTH, params.get(OFFSET_FIELD)/4), OFFSET_FIELD_INIT);
    }

    /**
    * Resolve the relevant predictor (or the default prediction) and predict the
    * result of the branch statement.
    * Adjust the PC as needed.
    */
    public void makePrediction(int offset_field) throws IrregularWriteOperationException, TwosComplementSumException, IrregularStringOfBitsException, IrregularWriteOperationException {
      BitSet64 bs;
      Register pc, b_pc;
      String pc_old, pc_new, offset;
      boolean predictIsTaken;
      switch(cpu.getPredictionMode()) {
        case TAKEN:
          cpu.isPredictable = true;
          /* Do prediction. In this case we always assume taken.*/
          bs=new BitSet64();
          bs.writeHalf(params.get(offset_field));
          offset=bs.getBinString();
          pc=cpu.getPC();
          b_pc=cpu.getBPC();
          pc_old=cpu.getPC().getBinString();
          pc_new=InstructionsUtils.twosComplementSum(pc_old,offset);
          b_pc.setBits(pc_old, 0);
          pc.setBits(pc_new,0);
          logger.info(">> set PC to "+pc.getHexString()+"\n---------------------------------------------");
          break;
        case LOCAL:
        case GLOBALCORRELATED:
          predictIsTaken = cpu.getPrediction(this);
          if(predictIsTaken) {
            bs=new BitSet64();
            bs.writeHalf(params.get(offset_field));
            offset=bs.getBinString();
            pc=cpu.getPC();
            b_pc=cpu.getBPC();
            pc_old=cpu.getPC().getBinString();
            pc_new=InstructionsUtils.twosComplementSum(pc_old,offset);
            b_pc.setBits(pc_old, 0);
            pc.setBits(pc_new,0);
            logger.info("L>> set PC to "+pc.getHexString()+"\n---------------------------------------------");
          }
          break;
        case NOTTAKEN:
        default:
          break;
      }
    }

    public void makePrediction() throws IrregularWriteOperationException, TwosComplementSumException, IrregularStringOfBitsException, IrregularWriteOperationException {
      makePrediction(OFFSET_FIELD);
    }

    /**
    * Respond to the resolved branch condition.
    */
    public void respondToCondition(boolean condition, String offset) throws IrregularWriteOperationException, TwosComplementSumException, IrregularStringOfBitsException, MispredictTakenException {
      boolean predictIsTaken; // Was the prediction to take the branch?
      cpu.predictionsTotal++;
      switch (cpu.getPredictionMode()) {
        case LOCAL:
        case GLOBALCORRELATED:
          predictIsTaken = cpu.getPrediction(this);
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
          cpu.predictionsCorrect++;
          if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL || cpu.getPredictionMode() == CPU.PREDICTIONMode.GLOBALCORRELATED) { // Update the BHT as necessary
            logger.info("L>> Branch correctly predicted taken, updating BHT");
            cpu.updatePrediction(this, true);
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
          if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL || cpu.getPredictionMode() == CPU.PREDICTIONMode.GLOBALCORRELATED) {
            logger.info("L>> Branch incorrectly predicted not taken, updating BHT");
            cpu.updatePrediction(this, true);
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
          String pc_b_h = cpu.getBPC().getHexString();
          pc.setBits(pc_b, 0);
          if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL || cpu.getPredictionMode() == CPU.PREDICTIONMode.GLOBALCORRELATED) {
            cpu.updatePrediction(this, false);
            Register b_pc = cpu.getBPC();
            b_pc.setBits("0", 0);
            logger.info("L>> Branch not taken, incorrectly predicted taken, updating BHT");
          } else {
            logger.info("L>> Branch not taken, incorrectly predicted taken");
          }
          logger.info(">> Setting PC to " + pc_b_h + "\n---------------------------------------------");
          throw new MispredictTakenException();
        }
        else {  // Prediction was correct
          cpu.predictionsCorrect++;
          if (cpu.getPredictionMode() == CPU.PREDICTIONMode.LOCAL || cpu.getPredictionMode() == CPU.PREDICTIONMode.GLOBALCORRELATED) {
            cpu.updatePrediction(this,false);
            logger.info("L>> Correctly predicted not taken, updating BHT");
          } else {
            logger.info("L>> Correctly predicted not taken");
          }
        }
  		}
    }

}
