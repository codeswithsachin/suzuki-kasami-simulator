# Presentation notes: Suzuki–Kasami Simulator

## 1. Problem (20 seconds)
In a distributed system there is no shared memory and no global clock. Several processes must not use the
critical section (CS) at the same time. Suzuki–Kasami solves this with a **token**: only the process that
holds the token may enter the CS.

## 2. The algorithm (1 minute)
Each process Pi keeps an array **RN[j]** = highest request number it has seen from Pj.
The token carries **LN[j]** = request number of Pj that was served last, and a **queue Q**.

1. Pi wants the CS and has no token: it increments RN[i] and sends REQUEST(i, RN[i]) to everyone.
2. Pj receives it: RN[i] = max(RN[i], sn). If Pj is idle and holds the token and RN[i] = LN[i] + 1, it sends the token to Pi.
3. Pi gets the token and enters the CS.
4. On exit Pi sets LN[i] = RN[i]. For every k with RN[k] = LN[k] + 1 that is not in Q, it appends k to Q.
5. If Q is not empty, Pi removes the first process and sends it the token.

## 3. Live demo script (3 minutes)
1. Click **Scenarios > Three simultaneous requests**, then press **Step** repeatedly.
   Show: P1, P2, P3 broadcast requests, RN arrays fill in, the token goes to P1.
2. Point at the **Token card**: LN and queue Q change as processes leave the CS.
3. Click **Scenarios > Token holder requests again**: P0 enters the CS with **0 messages** (it already has the token).
4. Click **Scenarios > Out-of-order network**, press Play: requests overtake each other, still SAFE.
5. Use **Step back** to undo an event and explain it again.
6. Open **Setup...**, set 8 processes, press Apply, click a few processes to request.
7. Show the metrics: messages per CS entry is at most N, against 2(N-1) for Ricart–Agrawala.

## 4. Design (30 seconds)
Engine and GUI are separate. `Simulation` is a discrete-event simulator with a virtual clock and a seeded random
generator, so a run is repeatable. The self-test runs 1.2 million random events with zero safety violations.

## 5. Viva questions and answers
**Q: What type of algorithm is it?** Token-based, broadcast, distributed mutual exclusion.

**Q: How many messages per CS entry?** N: (N-1) REQUESTs plus 1 TOKEN. Zero if the requester already holds the token.

**Q: What is the synchronization delay?** One message delay T (the token goes directly to the next process).

**Q: Compare with Ricart–Agrawala.** Ricart–Agrawala is permission-based and needs 2(N-1) messages per entry.
Suzuki–Kasami needs N, but it needs a token and cannot recover if the token is lost.

**Q: How does it avoid deadlock?** There is one token and a process only waits for it. The token always moves on to a requester.

**Q: How does it avoid starvation?** The token queue Q is FIFO, so every request is eventually served.

**Q: What if a REQUEST is delayed or arrives twice, or out of order?** RN[j] = max(RN[j], sn) and the test RN[j] = LN[j] + 1
mean an old or duplicate request is ignored. That is why the algorithm works on non-FIFO channels.

**Q: Why does the token carry LN and Q?** So the next holder knows which requests are already served and who is waiting.

**Q: What if the token is lost or a site crashes?** The basic algorithm does not handle it. It needs token regeneration or fault-tolerant extensions.

**Q: Where can it be used?** Distributed databases, distributed file systems, cloud resource locking.
