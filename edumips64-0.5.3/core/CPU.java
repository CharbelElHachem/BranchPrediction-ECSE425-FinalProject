/* CPU.java
 *
 * This class models a MIPS CPU with 32 64-bit General Purpose Register.
 * (c) 2006 Andrea Spadaccini, Simona Ullo, Antonella Scandura
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

package edumips64.core;

import java.util.*;
import java.util.logging.Logger;
import edumips64.core.is.*;
import edumips64.utils.*;

/** This class models a MIPS CPU with 32 64-bit General Purpose Registers.
*  @author Andrea Spadaccini, Simona Ullo, Antonella Scandura
*/
public class CPU
{
	private Memory mem;
	private Register[] gpr;
  private static final Logger logger = Logger.getLogger(CPU.class.getName());


    /** Program Counter*/
	private Register pc,old_pc,b_pc;
	private Register LO,HI;

  /** Pipeline status*/
  public enum PipeStatus {IF, ID, EX, MEM, WB};

	/** CPU status.
	 * 	READY - the CPU has been initialized but the symbol table hasn't been
	 *  already filled by the Parser. This means that you can't call the step()
	 *  method, or you'll get a StoppedCPUException.
	 *
	 * 	RUNNING - the CPU is executing a program, you can call the step()
	 * 	method, and the CPU will fetch additional instructions from the symbol
	 * 	table
	 *
	 * 	STOPPING - the HALT instruction has entered in the pipeline. This means
	 * 	that no additional instructions must be fetched but the instructions
	 * 	that are already in the pipeline must be executed. THe step() method can
	 * 	be called, but won't fetch any other instruction
	 *
	 * 	HALTED - the HALT instruction has passed the WB state, and the step()
	 * 	method can't be executed.
	 * */
	public enum CPUStatus {READY, RUNNING, STOPPING, HALTED};
	public enum PREDICTIONMode {NOTTAKEN, TAKEN, LOCAL, GLOBALCORRELATED}
	private PREDICTIONMode predictMode;
	private CPUStatus status;

    /** CPU pipeline, each status contains an Instruction object*/
    private Map<PipeStatus, Instruction> pipe;
    private SymbolTable symTable;

    /** The current status of the pipeline.*/
    private PipeStatus currentPipeStatus;

    /** The code and data sections limits*/
    public static final int CODELIMIT = 1024;	// bus da 12 bit (2^12 / 4)
    public static final int DATALIMIT = 512;	// bus da 12 bit (2^12 / 8)

    //prediction memory buffer
    private int[] localTable;
    private int[][] patternHistoryTable;
	private int globalHistoryBuffer;
	private int historyBufferBitmask;
	private static final int ADDRESSBITS = 12;
	private static int NBITS = 2;
	private static int MBITS = 12;
	public boolean isPredictable;

	private static CPU cpu;

	/** Statistics */
	private int cycles, instructions, RAWStalls, takenStalls;

	/* Branching statistics */
	public int predictionsCorrect, predictionsTotal;

	/** Static initializer */
	static {
		cpu = null;
	}
	private CPU()
	{
		predictMode = PREDICTIONMode.GLOBALCORRELATED;
		// initialize prediction buffer to support indexing by ADDRESSBITS bits
		localTable = new int[(int)Math.pow(2, ADDRESSBITS)];
		for(int i = 0; i < localTable.length; i++) {
			localTable[i] = (int)Math.pow(2, NBITS-1) - 1; //weak not taken
		}
		// Initialize correlating pattern history table
		patternHistoryTable = new int[(int)Math.pow(2, ADDRESSBITS)][(int)Math.pow(2, MBITS)];
		for(int i = 0; i < patternHistoryTable.length; i++) {
			for(int j = 0; j< patternHistoryTable[0].length; j++) {
				patternHistoryTable[i][j] = (int)Math.pow(2, NBITS-1) - 1; //weak not taken
			}
		}
		globalHistoryBuffer = (int)Math.pow(2,MBITS-1)-1; //initialized with a balanced global history;
		historyBufferBitmask = (int)Math.pow(2,MBITS)-1;


		// To avoid future singleton problems
		Instruction dummy = Instruction.buildInstruction("BUBBLE");

		logger.info("Creating the CPU...");
		cycles = 0;
		status = CPUStatus.READY;
		mem = Memory.getInstance();
		logger.info("Got Memory instance..");
		symTable = SymbolTable.getInstance();
		logger.info("Got SymbolTable instance..");

		// Registers initialization
		gpr = new Register[32];
		gpr[0] = new R0();
		for(int i=1;i<32;i++)
			gpr[i] = new Register();
		pc = new Register();
		old_pc = new Register();
		b_pc = new Register();
		LO=new Register();
		HI=new Register();

		// Pipeline initialization
		pipe = new HashMap<PipeStatus, Instruction>();
		clearPipe();
		currentPipeStatus = PipeStatus.IF;


		logger.info("CPU Created.");
	}

	public PREDICTIONMode getPredictionMode() {
		return predictMode;
	}

	public float getPredictionAccuracy() {
		if (predictionsTotal != 0) {
			return (float)predictionsCorrect/(float)predictionsTotal;
		}
		else {
			return Float.NaN;
		}
	}

	public int getBranches() {
		return predictionsTotal;
	}

	/** Sets the CPU status.
	 *  @param status a CPUStatus value
	 */
	public  void setStatus(CPUStatus status) {
		this.status = status;
	}

	/** Gets the CPU status
	 *  @return status a CPUStatus value representing the current CPU status
	 */
	public CPUStatus getStatus() {
		return status;
	}

    private void clearPipe()
    {
        pipe.put(PipeStatus.IF, null);
        pipe.put(PipeStatus.ID, null);
        pipe.put(PipeStatus.EX, null);
        pipe.put(PipeStatus.MEM, null);
        pipe.put(PipeStatus.WB, null);
    }

	public static CPU getInstance()
    {
		if(cpu == null)
			cpu = new CPU();
		return cpu;
	}

    public Register[] getRegisters()
    {
        return gpr;
    }

    public Memory getMemory()
    {
        return mem;
    }

    public SymbolTable getSymbolTable()
    {
        return symTable;
    }

    /** This method returns a specific GPR
    * @param index the register number (0-31)
    */
    public Register getRegister(int index)
    {
        return gpr[index];
    }

    public Map<PipeStatus, Instruction> getPipeline()
    {
        return pipe;
    }

	/** Returns the number of cycles performed by the CPU.
	 *  @return an integer
	 */
	public int getCycles() {
		return cycles;
	}

	/** Returns the number of instructions executed by the CPU
	 *  @return an integer
	 */
	public int getInstructions() {
		return instructions;
	}

	/** Returns the number of RAW Stalls that happened inside the pipeline
	 * @return an integer
	 */
	public int getRAWStalls() {
		return RAWStalls;
	}
	public int getTakenStalls() {
		return takenStalls;
	}
	public void incrementTakenStalls(){
        takenStalls++;
	}

    /** This method performs a single pipeline step
    * @throw RAWHazardException when a RAW hazard is detected
    */
	//branch prediction functions
	//return true if the branch prediction is "taken" , false if the branch prediction is "not taken"
	public boolean getPrediction(Instruction inst) {
		int address = mem.getInstructionIndex(inst) * 4;	// Each instruction is 4 bytes
		int predictorNum = address % localTable.length;
		if(predictMode == cpu.predictMode.LOCAL) {
			if (localTable[predictorNum]< Math.pow(2, NBITS - 1)) {	// Prediction is not taken
				return false;
			} else {														// Prediction is taken
				return true;
			}
		}else if(predictMode == cpu.predictMode.GLOBALCORRELATED) {
			if (patternHistoryTable[predictorNum][globalHistoryBuffer] < Math.pow(2, NBITS - 1)) {
				return false;
			} else {
				return true;
			}
		}else {
			return false;//should never happens
		}

	}

	/**
	* Update a local n-bit predictor for the given address.
	*/
	public void updatePrediction(Instruction inst, boolean wasTaken) {
		int address = mem.getInstructionIndex(inst) * 4;
		int predictorNum = address % localTable.length;
		int oldVal = localTable[predictorNum];
		if(predictMode == cpu.predictMode.LOCAL) {
			if(localTable[predictorNum] < Math.pow(2, NBITS) - 1 && wasTaken) {
				localTable[predictorNum]+=1;
			}else if(localTable[predictorNum] > 0 && !wasTaken) {
				localTable[predictorNum]-=1;
			}
			logger.info(">>> updated: "+predictorNum+" from " + oldVal + " to " + localTable[predictorNum]);
		}else if(predictMode == cpu.predictMode.GLOBALCORRELATED) {
			if(patternHistoryTable[predictorNum][globalHistoryBuffer] < Math.pow(2, NBITS) - 1 && wasTaken) {
				patternHistoryTable[predictorNum][globalHistoryBuffer]+=1;
			}else if(patternHistoryTable[predictorNum][globalHistoryBuffer] > 0 && !wasTaken) {
				patternHistoryTable[predictorNum][globalHistoryBuffer]-=1;
			}
			globalHistoryBuffer=((globalHistoryBuffer << 1) & historyBufferBitmask) | (wasTaken?0x1:0x0);
		}

    /*for(int i=0; i < localTable.length;i++) {
				logger.info(i+" -> "+localTable[i]);
		}*/
	}


  public void step() throws MispredictTakenException, IntegerOverflowException, AddressErrorException, HaltException, IrregularWriteOperationException, StoppedCPUException, MemoryElementNotFoundException, IrregularStringOfBitsException, TwosComplementSumException, SynchronousException, BreakException
	{
		/* The integer "breaking" is used to keep track of the BREAK
		 * instruction. When the BREAK instruction enters ID, the BreakException
		 * is thrown. We continue the normal cpu step flow, and at the end of
		 * this flow the BreakException is re-thrown.
		 */
		int breaking = 0;

		// Used for exception handling
		boolean masked = (Boolean)Config.get("syncexc-masked");
		boolean terminate = (Boolean)Config.get("syncexc-terminate");
		String syncex = null;

		if(status != CPUStatus.RUNNING && status != CPUStatus.STOPPING)
			throw new StoppedCPUException();
		try
		{
			logger.info("Starting cycle " + ++cycles + "\n---------------------------------------------");
			currentPipeStatus = PipeStatus.WB;

			// Let's execute the WB() method of the instruction located in the
			// WB pipeline status
			if(pipe.get(PipeStatus.WB)!=null) {
				pipe.get(PipeStatus.WB).WB();
				if(!pipe.get(PipeStatus.WB).getName().equals(" "))
					instructions++;
			}

			// We put null in WB, in order to avoid that an exception thrown in
			// the next instruction leaves the already completed instruction in
			// the WB pipeline state
			pipe.put(PipeStatus.WB, null);

			// MEM
			currentPipeStatus = PipeStatus.MEM;
			if(pipe.get(PipeStatus.MEM)!=null)
				pipe.get(PipeStatus.MEM).MEM();
			pipe.put(PipeStatus.WB, pipe.get(PipeStatus.MEM));

			// EX
			try {
				// Handling synchronous exceptions
				currentPipeStatus = PipeStatus.EX;
				if(pipe.get(PipeStatus.EX)!=null)
					pipe.get(PipeStatus.EX).EX();
			}
			catch (SynchronousException e) {
				if(masked)
					logger.info("[EXCEPTION] [MASKED] " + e.getCode());
				else {
					if(terminate) {
						logger.info("Terminating due to an unmasked exception");
						throw new SynchronousException(e.getCode());
					}
					else
						// We must complete this cycle, but we must notify the user.
						// If the syncex string is not null, the CPU code will throw
						// the exception at the end of the step
						syncex = e.getCode();
				}
			}
			pipe.put(PipeStatus.MEM, pipe.get(PipeStatus.EX));

			// ID
			currentPipeStatus = PipeStatus.ID;
			if(pipe.get(PipeStatus.ID)!=null)
				pipe.get(PipeStatus.ID).ID();
			pipe.put(PipeStatus.EX, pipe.get(PipeStatus.ID));

			// IF
			// We don't have to execute any methods, but we must get the new
			// instruction from the symbol table.
			currentPipeStatus = PipeStatus.IF;

			if(status == CPUStatus.RUNNING) {
				if(pipe.get(PipeStatus.IF) != null) { //rispetto a dinmips scambia le load con le IF
					isPredictable = false;
					try {
						pipe.get(PipeStatus.IF).IF();
					}
					catch (BreakException exc) {
						breaking = 1;
						logger.info("breaking = 1");
					}
				}
				//redundant
				if(!isPredictable) {
					pipe.put(PipeStatus.ID, pipe.get(PipeStatus.IF));
					pipe.put(PipeStatus.IF, mem.getInstruction(pc));
					old_pc.writeDoubleWord((pc.getValue()));
					pc.writeDoubleWord((pc.getValue())+4);
				}else {
					//do branch prediction
					//let's assume always taken if it is predictable
					//we do the same as the above however pc needs to be the target address
					//we compute that in the branchs IF()
					//pc is updated to offset lets update next IF with target instruction
					pipe.put(PipeStatus.ID, pipe.get(PipeStatus.IF));
					pipe.put(PipeStatus.IF, mem.getInstruction(pc));
					old_pc.writeDoubleWord((pc.getValue()));
					pc.writeDoubleWord((pc.getValue())+4);
				}

			}
			else
			{
				pipe.put(PipeStatus.ID, Instruction.buildInstruction("BUBBLE"));
			}
			if(breaking == 1) {
				breaking = 0;
				logger.info("Re-thrown the exception");
				throw new BreakException();
			}
			if(syncex != null)
				throw new SynchronousException(syncex);
		}
		catch(JumpException ex)
		{
      try {
          if(pipe.get(PipeStatus.IF) != null) //rispetto a dimips scambia le load con le IF
                  pipe.get(PipeStatus.IF).IF();
      }
      catch(BreakException bex) {
				logger.info("Caught a BREAK after a Jump: ignoring it.");
      }
			// A J-Type instruction has just modified the Program Counter. We need to
			// put in the IF state the instruction the PC points to
			pipe.put(PipeStatus.IF, mem.getInstruction(pc));
			pipe.put(PipeStatus.EX, pipe.get(PipeStatus.ID));
			pipe.put(PipeStatus.ID, Instruction.buildInstruction("BUBBLE"));
			old_pc.writeDoubleWord((pc.getValue()));
			pc.writeDoubleWord((pc.getValue())+4);
			if(syncex != null)
				throw new SynchronousException(syncex);

		}
		catch(MispredictTakenException ex)
		{
      try {
          if(pipe.get(PipeStatus.IF) != null) //rispetto a dimips scambia le load con le IF
                  pipe.get(PipeStatus.IF).IF();
      }
      catch(BreakException bex) {
				logger.info("Caught a BREAK after a Jump: ignoring it.");
      }

      incrementTakenStalls();

			// A J-Type instruction has just modified the Program Counter. We need to
			// put in the IF state the instruction the PC points to
			pipe.put(PipeStatus.IF, mem.getInstruction(pc));
			pipe.put(PipeStatus.EX, pipe.get(PipeStatus.ID));
			pipe.put(PipeStatus.ID, Instruction.buildInstruction("BUBBLE"));
			old_pc.writeDoubleWord((pc.getValue()));
			pc.writeDoubleWord((pc.getValue())+4);
			if(syncex != null)
				throw new SynchronousException(syncex);

		}
		catch(RAWException ex)
		{
			if(currentPipeStatus == PipeStatus.ID)
				pipe.put(PipeStatus.EX, Instruction.buildInstruction("BUBBLE"));
			RAWStalls++;
			if(syncex != null)
				throw new SynchronousException(syncex);

		}
		catch(SynchronousException ex) {
			logger.info("Exception: " + ex.getCode());
			throw ex;
		}
		catch(HaltException ex)
		{
			pipe.put(PipeStatus.WB, null);
			throw ex;
		}
	}

	/** Gets the Program Counter register
	 *  @return a Register object
	 */
	public Register getPC() {
		return pc;
	}
	/** Gets the Last Program Counter register
	 *  @return a Register object
	 */
	public Register getLastPC() {
		return old_pc;
	}
	//pc before branch taken
	public Register getBPC() {
		return b_pc;
	}

	/** Gets the LO register. It contains integer results of doubleword division
	* @return a Register object
	*/
	public Register getLO() {
		return LO;
	}

	/** Gets the HI register. It contains integer results of doubleword division
	* @return a Register object
	*/
	public Register getHI(){
		return HI;
	}

    /** This method resets the CPU components (GPRs, memory,statistics,
    *   PC, pipeline and Symbol table).
	*   It resets also the Dinero Tracefile object associated to the current
	*   CPU.
    */
    public void reset()
    {

    	//reset local branch history table
		for(int i=0; i < localTable.length;i++) {
			localTable[i]=(int)Math.pow(2, NBITS - 1) - 1;//weak not taken
		}
		// reset pattern history table
		for(int i = 0; i < patternHistoryTable.length; i++) {
			for(int j = 0; j< patternHistoryTable[0].length; j++) {
				patternHistoryTable[i][j] = (int)Math.pow(2, NBITS-1) - 1; //weak not taken
			}
		}

		// Reset stati della CPU
		status = CPUStatus.READY;
		cycles = 0;
		instructions = 0;
		RAWStalls = 0;
		takenStalls = 0;
		// Reset dei registri
        for(int i = 0; i < 32; i++)
            gpr[i].reset();

		LO.reset();
		HI.reset();

		// Reset program counter
        pc.reset();
		old_pc.reset();
		b_pc.reset();

		// Reset memoria
        mem.reset();

		// Reset pipeline
        clearPipe();

		// Reset Symbol table
        symTable.reset();

		// Reset tracefile
		Dinero.getInstance().reset();

		// Reset branch Statistics
		predictionsCorrect = 0;
		predictionsTotal = 0;

		logger.info("CPU Resetted");
    }

	/** Test method that returns a string containing the status of the pipeline.
	 * @returns a string
	 */
	public String pipeLineString() {
		String s = new String();
		s += "IF:\t" + pipe.get(PipeStatus.IF) + "\n";
		s += "ID:\t" + pipe.get(PipeStatus.ID) + "\n";
		s += "EX:\t" + pipe.get(PipeStatus.EX) + "\n";
		s += "MEM:\t" + pipe.get(PipeStatus.MEM) + "\n";
		s += "WB:\t" + pipe.get(PipeStatus.WB) + "\n";

		return s;
	}

	/** Test method that returns a string containing the values of every
	 * register.
	 * @returns a string
	 */
	public String gprString() {
		String s = new String();

		int i = 0;
		for(Register r : gpr)
			s += "Registro " + i++ + ":\t" + r.toString() + "\n";

		return s;
	}


	public String toString() {
		String s = new String();
		s += mem.toString() + "\n";
		s += pipeLineString();
		s += gprString();
		return s;
	}

	/** Private class, representing the R0 register */
	// TODO: DEVE IMPOSTARE I SEMAFORI?????
	private class R0 extends Register {
		public long getValue() {
			return (long)0;
		}
		public String getBinString()
		{
			return "0000000000000000000000000000000000000000000000000000000000000000";
		}
		public String getHexString()
		{
			return "0000000000000000";
		}
		public void setBits(String bits, int start) {
		}
		public void writeByteUnsigned(int value){}
		public void writebyte(int value){}
		public void writeByte(int value,int offset){}
		public void writeHalfUnsigned(int value){}
		public void writeHalf(int value){}
		public void writeHalf(int value, int offset){}
		public void writeWordUnsigned(long value){}
		public void writeWord(int value){}
		public void writeWord(long value,int offset){}
		public void writeDoubleWord(long value){}

	}
}
