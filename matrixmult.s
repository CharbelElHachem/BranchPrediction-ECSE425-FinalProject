	.data
mat:	.word	1,2,3,4
result:	.word	1,2,3,4
temp:	.word	1,2,3,4
iter:	.word	10
	.code
	DADDI R1,R0,0		;iter=0
	DADDI R2,R0,0		;i=0
	DADDI R3,R0,0		;j=0
	DADDI R4,R0,0		;k=0
	LD    R5,iter(R0)
	DADDI R6,R0,2	
foriter:
	slt r7,r1,r5
	beqz r7,end
	daddi r2,r0,0
fori:
	slt r7,r2,r6
	beqz r7,endfori
	daddi r3,r0,0
forj:
	slt r7,r3,r6
	beqz r7,endforj
	daddi r8,r0,0
	daddi r4,r0,0
fork:
	slt r7,r4,r6
	beqz r7,endfork
	daddi r9,r0,2
	dmult r9,r4
	dadd r9,r9,r2
	ld r9,result(r9)
	daddi r10,r0,2
	dmult r10,r3
	dadd r10,r10,r4
	ld r10,result(r10)
	dmult r9,r10
	dadd r8,r8,r9
	daddi r4,r4,1
	j fork
endfork:
	daddi r9,r0,2
	dmult r9,r3
	dadd r9,r9,r2
	sd r8,temp(r9)
	daddi r3,r3,1
	j forj
endforj:
	daddi r2,r2,1
	j fori
endfori:
	dadd r9,r0,r0
	ld r10,temp(r9)
	sd r10,result(r9)
	daddi r9,r9,8
	ld r10,temp(r9)
	sd r10,result(r9)
	daddi r9,r9,8
	ld r10,temp(r9)
	sd r10,result(r9)
	daddi r9,r9,8
	ld r10,temp(r9)
	sd r10,result(r9)
	daddi r1,r1,1
	j foriter
end: HALT