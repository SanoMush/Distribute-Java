package edu.coursera.distributed;

import edu.coursera.distributed.util.MPI;
import edu.coursera.distributed.util.MPI.MPIException;

/**
 * A wrapper class for a parallel, MPI-based matrix multiply implementation.
 */
public class MatrixMult {
    /**
     * A parallel implementation of matrix multiply using MPI to express SPMD
     * parallelism. In particular, this method should store the output of
     * multiplying the matrices a and b into the matrix c.
     *
     * This method is called simultaneously by all MPI ranks in a running MPI
     * program. For simplicity MPI_Init has already been called, and
     * MPI_Finalize should not be called in parallelMatrixMultiply.
     *
     * On entry to parallelMatrixMultiply, the following will be true of a, b,
     * and c:
     *
     *   1) The matrix a will only be filled with the input values on MPI rank
     *      zero. Matrix a on all other ranks will be empty (initialized to all
     *      zeros).
     *   2) Likewise, the matrix b will only be filled with input values on MPI
     *      rank zero. Matrix b on all other ranks will be empty (initialized to
     *      all zeros).
     *   3) Matrix c will be initialized to all zeros on all ranks.
     *
     * Upon returning from parallelMatrixMultiply, the following must be true:
     *
     *   1) On rank zero, matrix c must be filled with the final output of the
     *      full matrix multiplication. The contents of matrix c on all other
     *      ranks are ignored.
     *
     * Therefore, it is the responsibility of this method to distribute the
     * input data in a and b across all MPI ranks for maximal parallelism,
     * perform the matrix multiply in parallel, and finally collect the output
     * data in c from all ranks back to the zeroth rank. You may use any of the
     * MPI APIs provided in the mpi object to accomplish this.
     *
     * A reference sequential implementation is provided below, demonstrating
     * the use of the Matrix class's APIs.
     *
     * @param a Input matrix
     * @param b Input matrix
     * @param c Output matrix
     * @param mpi MPI object supporting MPI APIs
     * @throws MPIException On MPI error. It is not expected that your
     *                      implementation should throw any MPI errors during
     *                      normal operation.
     */
    public static void parallelMatrixMultiply(Matrix a, Matrix b, Matrix c, final MPI mpi) throws MPIException {
        int worldSize = mpi.MPI_Comm_size(mpi.MPI_COMM_WORLD);
        int worldRank = mpi.MPI_Comm_rank(mpi.MPI_COMM_WORLD);

        int aRows = a.getNRows();
        int aCols = a.getNCols();
        int bCols = b.getNCols();

        // Step 1: Broadcast b to all ranks
        double[] bData = new double[b.getNRows() * b.getNCols()];
        if (worldRank == 0) {
            for (int i = 0; i < b.getNRows(); i++) {
                for (int j = 0; j < b.getNCols(); j++) {
                    bData[i * bCols + j] = b.get(i, j);
                }
            }
        }
        mpi.MPI_Bcast(bData, 0, bData.length, 0, mpi.MPI_COMM_WORLD);

        // Step 2: Scatter rows of a and compute partial result
        int rowsPerRank = aRows / worldSize;
        int remainder = aRows % worldSize;

        int localRows = rowsPerRank + (worldRank < remainder ? 1 : 0);
        int startRow = worldRank * rowsPerRank + Math.min(worldRank, remainder);

        double[] localA = new double[localRows * aCols];
        if (worldRank == 0) {
            for (int rank = 1; rank < worldSize; rank++) {
                int sendRows = rowsPerRank + (rank < remainder ? 1 : 0);
                int sendStartRow = rank * rowsPerRank + Math.min(rank, remainder);
                double[] sendBuf = new double[sendRows * aCols];

                for (int i = 0; i < sendRows; i++) {
                    for (int j = 0; j < aCols; j++) {
                        sendBuf[i * aCols + j] = a.get(sendStartRow + i, j);
                    }
                }
                mpi.MPI_Send(sendBuf, 0, sendBuf.length, rank, 0, mpi.MPI_COMM_WORLD);
            }

            // Fill localA for rank 0
            for (int i = 0; i < localRows; i++) {
                for (int j = 0; j < aCols; j++) {
                    localA[i * aCols + j] = a.get(startRow + i, j);
                }
            }
        } else {
            mpi.MPI_Recv(localA, 0, localA.length, 0, 0, mpi.MPI_COMM_WORLD);
        }

        // Step 3: Compute local result
        double[] localC = new double[localRows * bCols];
        for (int i = 0; i < localRows; i++) {
            for (int j = 0; j < bCols; j++) {
                double sum = 0.0;
                for (int k = 0; k < aCols; k++) {
                    sum += localA[i * aCols + k] * bData[k * bCols + j];
                }
                localC[i * bCols + j] = sum;
            }
        }

        // Step 4: Gather results into matrix c on rank 0
        if (worldRank == 0) {
            // Copy local result to c
            for (int i = 0; i < localRows; i++) {
                for (int j = 0; j < bCols; j++) {
                    c.set(startRow + i, j, localC[i * bCols + j]);
                }
            }

            for (int rank = 1; rank < worldSize; rank++) {
                int recvRows = rowsPerRank + (rank < remainder ? 1 : 0);
                int recvStartRow = rank * rowsPerRank + Math.min(rank, remainder);
                double[] recvBuf = new double[recvRows * bCols];

                mpi.MPI_Recv(recvBuf, 0, recvBuf.length, rank, 1, mpi.MPI_COMM_WORLD);
                for (int i = 0; i < recvRows; i++) {
                    for (int j = 0; j < bCols; j++) {
                        c.set(recvStartRow + i, j, recvBuf[i * bCols + j]);
                    }
                }
            }
        } else {
            mpi.MPI_Send(localC, 0, localC.length, 0, 1, mpi.MPI_COMM_WORLD);
        }
    }

}
