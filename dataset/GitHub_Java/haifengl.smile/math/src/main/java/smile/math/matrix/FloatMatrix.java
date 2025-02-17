/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package smile.math.matrix;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import smile.math.MathEx;
import smile.math.blas.*;
import smile.sort.QuickSort;

public class FloatMatrix extends MatrixBase implements MatrixVectorMultiplication<float[]>, Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FloatMatrix.class);

    /**
     * The matrix storage.
     */
    private transient FloatBuffer A;
    /**
     * The leading dimension.
     */
    private transient int ld;
    /**
     * The number of rows.
     */
    private int m;
    /**
     * The number of columns.
     */
    private int n;
    /**
     * The packed storage format compactly stores matrix elements
     * when only one part of the matrix, the upper or lower
     * triangle, is necessary to determine all of the elements of the matrix.
     * This is the case when the matrix is upper triangular, lower triangular,
     * symmetric, or Hermitian.
     */
    private UPLO uplo = null;
    /**
     * The flag if a triangular matrix has unit diagonal elements.
     */
    private Diag diag = null;

    /**
     * Constructor of zero matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     */
    public FloatMatrix(int m, int n) {
        this(m, n, 0.0f);
    }

    /**
     * Constructor. Fills the matrix with given value.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param a the initial value.
     */
    public FloatMatrix(int m, int n, float a) {
        this.m = m;
        this.n = n;
        this.ld = m;

        float[] array = new float[m * n];
        if (a != 0.0) Arrays.fill(array, a);
        A = FloatBuffer.wrap(array);
    }

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public FloatMatrix(float[][] A) {
        this(A.length, A[0].length);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                set(i, j, A[i][j]);
            }
        }
    }

    /**
     * Constructor of a column vector/matrix with given array as the internal storage.
     * @param A The array of column vector.
     */
    public FloatMatrix(float[] A) {
        this(A, 0, A.length);
    }

    /**
     * Constructor of a column vector/matrix with given array as the internal storage.
     * @param A The array of column vector.
     * @param offset The offset of the subarray to be used; must be non-negative and
     *               no larger than array.length.
     * @param length The length of the subarray to be used; must be non-negative and
     *               no larger than array.length - offset.
     */
    public FloatMatrix(float[] A, int offset, int length) {
        this.m = length;
        this.n = 1;
        this.ld = length;
        this.A = FloatBuffer.wrap(A, offset, length);
    }

    /**
     * Constructor.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param ld the leading dimension.
     * @param A the matrix storage.
     */
    public FloatMatrix(int m, int n, int ld, FloatBuffer A) {
        if (layout() == Layout.COL_MAJOR && ld < m) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for COL_MAJOR: %d < %d", ld, m));
        }

        if (layout() == Layout.ROW_MAJOR && ld < n) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for ROW_MAJOR: %d < %d", ld, n));
        }

        this.m = m;
        this.n = n;
        this.ld = ld;
        this.A = A;
    }

    /**
     * Returns an n-by-n identity matrix.
     * @param n the number of rows/columns.
     */
    public static FloatMatrix eye(int n) {
        FloatMatrix matrix = new FloatMatrix(n, n);

        for (int i = 0; i < n; i++) {
            matrix.set(i, i, 1.0f);
        }

        return matrix;
    }

    /**
     * Returns an m-by-n identity matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     */
    public static FloatMatrix eye(int m, int n) {
        FloatMatrix matrix = new FloatMatrix(m, n);

        int k = Math.min(m, n);
        for (int i = 0; i < k; i++) {
            matrix.set(i, i, 1.0f);
        }

        return matrix;
    }

    /**
     * Returns a square diagonal matrix with the elements of vector
     * v on the main diagonal.
     *
     * @param v the diagonal elements.
     */
    public static FloatMatrix diag(float[] v) {
        int n = v.length;
        FloatMatrix D = new FloatMatrix(n, n);
        for (int i = 0; i < n; i++) {
            D.set(i, i, v[i]);
        }
        return D;
    }

    /** Customized object serialization. */
    private void writeObject(ObjectOutputStream out) throws IOException {
        // write default properties
        out.defaultWriteObject();
        // leading dimension is compacted to m
        out.writeInt(m);
        // write buffer
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                out.writeFloat(get(i, j));
            }
        }
    }

    /** Customized object serialization. */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        //read default properties
        in.defaultReadObject();
        this.ld = in.readInt();

        // read buffer data
        int size = m * n;
        float[] buffer = new float[size];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                set(i, j, in.readFloat());
            }
        }
        this.A = FloatBuffer.wrap(buffer);
    }

    @Override
    public int nrows() {
        return m;
    }

    @Override
    public int ncols() {
        return n;
    }

    /**
     * Returns the matrix layout.
     */
    public Layout layout() {
        return Layout.COL_MAJOR;
    }

    /**
     * Returns the leading dimension.
     */
    public int ld() {
        return ld;
    }

    /**
     * Returns if the matrix is a submatrix.
     */
    public boolean isSubmatrix() {
        if (layout() == Layout.COL_MAJOR) {
            return ld == m;
        } else {
            return ld == n;
        }
    }

    /**
     * Return if the matrix is symmetric (uplo != null && diag == null).
     */
    public boolean isSymmetric() {
        return uplo != null && diag == null;
    }

    /** Sets the format of packed matrix. */
    public FloatMatrix uplo(UPLO uplo) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        this.uplo = uplo;
        return this;
    }

    /** Gets the format of packed matrix. */
    public UPLO uplo() {
        return uplo;
    }

    /** Sets the format if a triangular matrix has unit diagonal elements. */
    public FloatMatrix diag(Diag diag) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        this.diag = diag;
        return this;
    }

    /** Gets the flag if a triangular matrix has unit diagonal elements. */
    public Diag diag() {
        return diag;
    }

    @Override
    public FloatMatrix clone() {
        FloatMatrix matrix = new FloatMatrix(m, n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, get(i, j));
            }
        }

        if (m == n) {
            matrix.uplo(uplo);
            matrix.diag(diag);
        }

        return matrix;
    }

    /**
     * Fill the matrix with a value.
     */
    public void fill(float x) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                set(i, j, x);
            }
        }
    }

    /**
     * Returns the transposed matrix.
     */
    public FloatMatrix transpose() {
        return new FloatMatrix(n, m, ld, A) {
            @Override
            public Layout layout() {
                return Layout.ROW_MAJOR;
            }

            @Override
            protected int index(int i , int j) {
                return i * ld + j;
            }

            @Override
            public FloatMatrix transpose() {
                return new FloatMatrix(n, m, ld, A);
            }
        };
    }

    /** Returns the linear index of matrix element. */
    protected int index(int i , int j) {
        return j * ld + i;
    }

    @Override
    public String str(int i, int j) {
        return String.format("%.4f", get(i, j));
    }

    /**
     * Gets A[i,j].
     */
    public float get(int i, int j) {
        return A.get(index(i, j));
    }

    /**
     * Sets A[i,j] = x.
     */
    public void set(int i, int j, float x) {
        A.put(index(i, j), x);
    }

    /**
     * A[i,j] += x
     */
    public float add(int i, int j, float x) {
        int k = index(i, j);
        float y = A.get(k) + x;
        A.put(k, y);
        return y;
    }

    /**
     * A[i,j] -= x
     */
    public float sub(int i, int j, float x) {
        int k = index(i, j);
        float y = A.get(k) - x;
        A.put(k, y);
        return y;
    }

    /**
     * A[i,j] *= x
     */
    public float mul(int i, int j, float x) {
        int k = index(i, j);
        float y = A.get(k) * x;
        A.put(k, y);
        return y;
    }

    /**
     * A[i,j] /= x
     */
    public float div(int i, int j, float x) {
        int k = index(i, j);
        float y = A.get(k) / x;
        A.put(k, y);
        return y;
    }

    /** Rank-1 update A += alpha * x * y' */
    public FloatMatrix add(float alpha, float[] x, float[] y) {
        if (m != x.length || n != y.length) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (isSymmetric() && x == y) {
            BLAS.engine.syr(layout(), uplo, m, alpha, FloatBuffer.wrap(x), 1, A, ld);
        } else {
            BLAS.engine.ger(layout(), m, n, alpha, FloatBuffer.wrap(x), 1, FloatBuffer.wrap(y), 1, A, ld);
        }

        return this;
    }

    /** Element-wise addition A += B */
    public FloatMatrix add(FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                add(i, j, B.get(i, j));
            }
        }
        return this;
    }

    /** Element-wise addition C = A + B */
    public FloatMatrix add(FloatMatrix B, FloatMatrix C) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix A and B are not of same size.");
        }

        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix A and C are not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) + B.get(i, j));
            }
        }
        return C;
    }

    /** Element-wise subtraction A -= B */
    public FloatMatrix sub(FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sub(i, j, B.get(i, j));
            }
        }
        return this;
    }

    /** Element-wise subtraction C = A - B */
    public FloatMatrix sub(FloatMatrix B, FloatMatrix C) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix A and B are not of same size.");
        }

        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix A and C are not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) - B.get(i, j));
            }
        }
        return C;
    }

    /** Element-wise multiplication A *= B */
    public FloatMatrix mul(FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                mul(i, j, B.get(i, j));
            }
        }
        return this;
    }

    /** Element-wise multiplication C = A * B */
    public FloatMatrix mul(FloatMatrix B, FloatMatrix C) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix A and B are not of same size.");
        }

        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix A and C are not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) * B.get(i, j));
            }
        }
        return C;
    }

    /** Element-wise division A /= B */
    public FloatMatrix div(FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, B.get(i, j));
            }
        }
        return this;
    }

    /** Element-wise division C = A / B */
    public FloatMatrix div(FloatMatrix B, FloatMatrix C) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix A and B are not of same size.");
        }

        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix A and C are not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) / B.get(i, j));
            }
        }
        return C;
    }

    /** A += b */
    public FloatMatrix add(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                add(i, j, b);
            }
        }

        return this;
    }

    /** C = A + b */
    public FloatMatrix add(float b, FloatMatrix C) {
        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) + b);
            }
        }

        return C;
    }

    /** A -= b */
    public FloatMatrix sub(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sub(i, j, b);
            }
        }

        return this;
    }

    /** C = A - b */
    public FloatMatrix sub(float b, FloatMatrix C) {
        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) - b);
            }
        }

        return C;
    }

    /** A *= b */
    public FloatMatrix mul(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                mul(i, j, b);
            }
        }

        return this;
    }

    /** C = A * b */
    public FloatMatrix mul(float b, FloatMatrix C) {
        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) * b);
            }
        }

        return C;
    }

    /** A /= b */
    public FloatMatrix div(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, b);
            }
        }

        return this;
    }

    /** C = A / b */
    public FloatMatrix div(float b, FloatMatrix C) {
        if (m != C.m || n != C.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                C.set(i, j, get(i, j) / b);
            }
        }

        return C;
    }

    /**
     * Replaces NaN's with given value.
     */
    public FloatMatrix replaceNaN(float x) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                if (Float.isNaN(get(i, j))) {
                    set(i, j, x);
                }
            }
        }

        return this;
    }

    /**
     * Returns the sum of all elements in the matrix.
     * @return the sum of all elements.
     */
    public float sum() {
        float s = 0.0f;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                s += get(i, j);
            }
        }

        return s;
    }

    /**
     * L1 matrix norm. Maximum column sum.
     */
    public float norm1() {
        float f = 0.0f;
        for (int j = 0; j < n; j++) {
            float s = 0.0f;
            for (int i = 0; i < m; i++) {
                s += Math.abs(get(i, j));
            }
            f = Math.max(f, s);
        }

        return f;
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    public float norm2() {
        return svd(false).s[0];
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    public float norm() {
        return norm2();
    }

    /**
     * Infinity matrix norm. Maximum row sum.
     */
    public float normInf() {
        float[] f = new float[m];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f[i] += Math.abs(get(i, j));
            }
        }

        return MathEx.max(f);
    }

    /**
     * Frobenius matrix norm. Sqrt of sum of squares of all elements.
     */
    public float normFro() {
        double f = 0.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f = Math.hypot(f, get(i, j));
            }
        }

        return (float) f;
    }

    /**
     * Returns x' * A * x.
     * The left upper submatrix of A is used in the computation based
     * on the size of x.
     */
    public float xax(float[] x) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        if (n != x.length) {
            throw new IllegalArgumentException(String.format("Matrix: %d x %d, Vector: %d", m, n, x.length));
        }

        int n = x.length;
        float s = 0.0f;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                s += get(i, j) * x[i] * x[j];
            }
        }

        return s;
    }

    /**
     * Returns the sum of each row.
     */
    public float[] rowSums() {
        float[] x = new float[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[i] += get(i, j);
            }
        }

        return x;
    }

    /**
     * Returns the mean of each row.
     */
    public float[] rowMeans() {
        float[] x = new float[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[i] += get(i, j);
            }
        }

        for (int i = 0; i < m; i++) {
            x[i] /= n;
        }

        return x;
    }

    /**
     * Returns the standard deviations of each row.
     */
    public float[] rowSds() {
        float[] x = new float[m];
        float[] x2 = new float[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                float a = get(i, j);
                x[i] += a;
                x2[i] += a * a;
            }
        }

        for (int i = 0; i < m; i++) {
            float mu = x[i] / n;
            x[i] = (float) Math.sqrt(x2[i] / n - mu * mu);
        }

        return x;
    }

    /**
     * Returns the sum of each column.
     */
    public float[] colSums() {
        float[] x = new float[n];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[j] += get(i, j);
            }
        }

        return x;
    }

    /**
     * Returns the mean of each column.
     */
    public float[] colMeans() {
        float[] x = new float[n];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[j] += get(i, j);
            }
            x[j] /= m;
        }

        return x;
    }

    /**
     * Returns the standard deviations of each column.
     */
    public float[] colSds() {
        float[] x = new float[n];

        for (int j = 0; j < n; j++) {
            float mu = 0.0f;
            float sumsq = 0.0f;
            for (int i = 0; i < m; i++) {
                float a = get(i, j);
                mu += a;
                sumsq += a * a;
            }
            mu /= m;
            x[j] = (float) Math.sqrt(sumsq / m - mu * mu);
        }

        return x;
    }

    /**
     * Centers and scales the columns of matrix.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    public FloatMatrix scale() {
        float[] center = colMeans();
        float[] scale = colSds();
        return scale(center, scale);
    }

    /**
     * Centers and scales the columns of matrix.
     * @param center column center. If null, no centering.
     * @param scale column scale. If null, no scaling.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    public FloatMatrix scale(float[] center, float[] scale) {
        if (center == null && scale == null) {
            throw new IllegalArgumentException("Both center and scale are null");
        }

        FloatMatrix matrix = new FloatMatrix(m, n);

        if (center == null) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    matrix.set(i, j, get(i, j) / scale[j]);
                }
            }
        } else if (scale == null) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    matrix.set(i, j, get(i, j) - center[j]);
                }
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    matrix.set(i, j, (get(i, j) - center[j]) / scale[j]);
                }
            }
        }

        return matrix;
    }

    /**
     * Returns the inverse matrix.
     */
    public FloatMatrix inverse() {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        FloatMatrix lu = clone();
        FloatMatrix inv = eye(n);
        int[] ipiv = new int[n];
        if (isSymmetric()) {
            int info = LAPACK.engine.sysv(lu.layout(), uplo,  n, n, lu.A, lu.ld(), IntBuffer.wrap(ipiv), inv.A, inv.ld());
            if (info != 0) {
                throw new ArithmeticException("SYSV fails: " + info);
            }
        } else {
            int info = LAPACK.engine.gesv(lu.layout(), n, n, lu.A, lu.ld(), IntBuffer.wrap(ipiv), inv.A, inv.ld());
            if (info != 0) {
                throw new ArithmeticException("GESV fails: " + info);
            }
        }

        return inv;
    }

    /**
     * Return the two-dimensional array of matrix.
     * @return the two-dimensional array of matrix.
     */
    public float[][] toArray() {
        float[][] array = new float[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                array[i][j] = get(i, j);
            }
        }
        return array;
    }

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A * x + beta * y
     * </code></pre>
     */
    public void mv(Transpose trans, float alpha, FloatBuffer x, float beta, FloatBuffer y) {
        if (uplo != null) {
            if (diag != null) {
                if (alpha == 1.0 && beta == 0.0 && x == y) {
                    BLAS.engine.trmv(layout(), uplo, trans, diag, m, A, ld(), y, 1);
                } else {
                    BLAS.engine.gemv(layout(), trans, m, n, alpha, A, ld(), x, 1, beta, y, 1);
                }
            } else {
                BLAS.engine.symv(layout(), uplo, m, alpha, A, ld(), x, 1, beta, y, 1);
            }
        } else {
            BLAS.engine.gemv(layout(), trans, m, n, alpha, A, ld(), x, 1, beta, y, 1);
        }
    }

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A * x + beta * y
     * </code></pre>
     */
    public void mv(Transpose trans, float alpha, float[] x, float beta, float[] y) {
        mv(trans, alpha, FloatBuffer.wrap(x), beta, FloatBuffer.wrap(y));
    }

    @Override
    public float[] mv(Transpose trans, float[] x) {
        float[] y = new float[trans == Transpose.NO_TRANSPOSE ? n : m];
        mv(trans, x, y);
        return y;
    }

    @Override
    public void mv(Transpose trans, float[] x, float[] y) {
        mv(trans, 1.0f, FloatBuffer.wrap(x), 0.0f, FloatBuffer.wrap(y));
    }

    @Override
    public void mv(Transpose trans, float[] work, int inputOffset, int outputOffset) {
        FloatBuffer xb = FloatBuffer.wrap(work, inputOffset, trans == Transpose.NO_TRANSPOSE ? n : m);
        FloatBuffer yb = FloatBuffer.wrap(work, outputOffset, trans == Transpose.NO_TRANSPOSE ? m : n);
        mv(trans, 1.0f, xb, 0.0f, yb);
    }

    /** Flips the transpose operation. */
    private Transpose flip(Transpose trans) {
        return trans == Transpose.NO_TRANSPOSE ? Transpose.TRANSPOSE : Transpose.NO_TRANSPOSE;
    }

    /**
     * Matrix-matrix multiplication.
     * <pre><code>
     *     C := alpha*A*B + beta*C
     * </code></pre>
     */
    public void mm(Transpose transA, Transpose transB, float alpha, FloatMatrix B, float beta, FloatMatrix C) {
        if (layout() != C.layout()) {
            throw new IllegalArgumentException();
        }

        if (isSymmetric()) {
            BLAS.engine.symm(C.layout(), Side.LEFT, uplo, C.m, C.n, alpha, A, ld(), B.A, B.ld, beta, C.A, C.ld);
        } else if (B.isSymmetric()) {
            BLAS.engine.symm(C.layout(), Side.RIGHT, uplo, C.m, C.n, alpha, B.A, B.ld, A, ld(), beta, C.A, C.ld);
        } else {
            if (C.layout() != layout()) transA = flip(transA);
            if (C.layout() != B.layout()) transB = flip(transB);
            int k = transA == Transpose.NO_TRANSPOSE ? n : m;

            BLAS.engine.gemm(layout(), transA, transB, C.m, C.n, k, alpha,  A, ld,  B.A, B.ld, beta, C.A, ld);
        }
    }

    /** Returns A' * A */
    public FloatMatrix ata() {
        FloatMatrix C = new FloatMatrix(n, n);
        mm(Transpose.TRANSPOSE, Transpose.NO_TRANSPOSE, 1.0f, transpose(), 0.0f, C);
        return C;
    }

    /** Returns A * A' */
    public FloatMatrix aat() {
        FloatMatrix C = new FloatMatrix(m, m);
        mm(Transpose.NO_TRANSPOSE, Transpose.TRANSPOSE, 1.0f, transpose(), 0.0f, C);
        return C;
    }

    /** Returns matrix multiplication A * B. */
    public FloatMatrix abmm(FloatMatrix B) {
        if (n != B.m) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(m, B.n);
        mm(Transpose.NO_TRANSPOSE, Transpose.NO_TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /** Returns matrix multiplication A * B'. */
    public FloatMatrix abtmm(FloatMatrix B) {
        if (n != B.n) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(m, B.m);
        mm(Transpose.NO_TRANSPOSE, Transpose.TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /** Returns matrix multiplication A' * B. */
    public FloatMatrix atbmm(FloatMatrix B) {
        if (m != B.m) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(n, B.n);
        mm(Transpose.TRANSPOSE, Transpose.NO_TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /** Returns matrix multiplication A' * B'. */
    public FloatMatrix atbtmm(FloatMatrix B) {
        if (m != B.n) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B': %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(n, B.m);
        mm(Transpose.TRANSPOSE, Transpose.TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /**
     * LU decomposition.
     */
    public LU lu() {
        FloatMatrix lu = clone();
        int[] ipiv = new int[Math.min(m, n)];
        int info = LAPACK.engine.getrf(lu.layout(), lu.m, lu.n, lu.A, lu.ld, IntBuffer.wrap(ipiv));
        if (info < 0) {
            logger.error("LAPACK GETRF error code: {}", info);
            throw new ArithmeticException("LAPACK GETRF error code: " + info);
        }

        return new LU(lu, ipiv, info);
    }

    /**
     * Cholesky decomposition for symmetric and positive definite matrix.
     *
     * @throws ArithmeticException if the matrix is not positive definite.
     */
    public Cholesky cholesky() {
        if (uplo == null) {
            throw new IllegalArgumentException("The matrix is not symmetric");
        }

        FloatMatrix L = clone();
        int info = LAPACK.engine.potrf(L.layout(), L.uplo, n, L.A, L.ld);
        if (info != 0) {
            logger.error("LAPACK GETRF error code: {}", info);
            throw new ArithmeticException("LAPACK GETRF error code: " + info);
        }

        return new Cholesky(L);
    }

    /**
     * QR Decomposition.
     */
    public QR qr() {
        FloatMatrix qr = clone();
        float[] tau = new float[Math.min(m, n)];
        int info = LAPACK.engine.geqrf(qr.layout(), qr.m, qr.n, qr.A, qr.ld, FloatBuffer.wrap(tau));
        if (info != 0) {
            logger.error("LAPACK GEQRF error code: {}", info);
            throw new ArithmeticException("LAPACK GEQRF error code: " + info);
        }

        return new QR(qr, tau);
    }

    /**
     * Singular Value Decomposition.
     * Returns an economy-size decomposition of m-by-n matrix A:
     * <ul>
     * <li>m > n — Only the first n columns of U are computed, and S is n-by-n.</li>
     * <li>m = n — Equivalent to full SVD.</li>
     * <li>m < n — Only the first m columns of V are computed, and S is m-by-m.</li>
     * </ul>
     * The economy-size decomposition removes extra rows or columns of zeros from
     * the diagonal matrix of singular values, S, along with the columns in either
     * U or V that multiply those zeros in the expression A = U*S*V'. Removing these
     * zeros and columns can improve execution time and reduce storage requirements
     * without compromising the accuracy of the decomposition.
     */
    public SVD svd() {
        return svd(true);
    }

    /**
     * Singular Value Decomposition.
     * Returns an economy-size decomposition of m-by-n matrix A:
     * <ul>
     * <li>m > n — Only the first n columns of U are computed, and S is n-by-n.</li>
     * <li>m = n — Equivalent to full SVD.</li>
     * <li>m < n — Only the first m columns of V are computed, and S is m-by-m.</li>
     * </ul>
     * The economy-size decomposition removes extra rows or columns of zeros from
     * the diagonal matrix of singular values, S, along with the columns in either
     * U or V that multiply those zeros in the expression A = U*S*V'. Removing these
     * zeros and columns can improve execution time and reduce storage requirements
     * without compromising the accuracy of the decomposition.
     *
     * @param vectors The flag if computing the singular vectors.
     */
    public SVD svd(boolean vectors) {
        int k = Math.min(m, n);
        float[] s = new float[k];

        if (vectors) {
            FloatMatrix U = new FloatMatrix(m, k);
            FloatMatrix VT = new FloatMatrix(k, n);
            FloatMatrix W = clone();

            int info = LAPACK.engine.gesdd(W.layout(), SVDJob.ECONOMY, W.m, W.n, W.A, W.ld, FloatBuffer.wrap(s), U.A, U.ld, VT.A, VT.ld);
            if (info != 0) {
                logger.error("LAPACK GESDD error code: {}", info);
                throw new ArithmeticException("LAPACK GESDD error code: " + info);
            }

            return new SVD(s, U, VT.transpose());
        } else {
            FloatMatrix U = new FloatMatrix(1, 1);
            FloatMatrix VT = new FloatMatrix(1, 1);
            FloatMatrix W = clone();

            int info = LAPACK.engine.gesdd(W.layout(), SVDJob.NO_VECTORS, W.m, W.n, W.A, W.ld, FloatBuffer.wrap(s), U.A, U.ld, VT.A, VT.ld);
            if (info != 0) {
                logger.error("LAPACK GESDD error code: {}", info);
                throw new ArithmeticException("LAPACK GESDD error code: " + info);
            }

            return new SVD(s, null, null);
        }
    }

    /**
     * Eigenvalue Decomposition. For a symmetric matrix, all eigenvalues are
     * real values. Otherwise, the eigenvalues may be complex numbers.
     * <p>
     * By default <code>eigen</code> does not always return the eigenvalues
     * and eigenvectors in sorted order. Use the <code>EVD.sort</code> function
     * to put the eigenvalues in descending order and reorder the corresponding
     * eigenvectors.
     */
    public EVD eigen() {
        return eigen(false, true);
    }

    /**
     * Eigenvalue Decomposition. For a symmetric matrix, all eigenvalues are
     * real values. Otherwise, the eigenvalues may be complex numbers.
     * <p>
     * By default <code>eigen</code> does not always return the eigenvalues
     * and eigenvectors in sorted order. Use the <code>sort</code> function
     * to put the eigenvalues in descending order and reorder the corresponding
     * eigenvectors.
     *
     * @param vl The flag if computing the left eigenvectors.
     * @param vr The flag if computing the right eigenvectors.
     */
    public EVD eigen(boolean vl, boolean vr) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        FloatMatrix eig = clone();
        if (isSymmetric()) {
            float[] w = new float[n];
            int info = LAPACK.engine.syevd(eig.layout(), vr ? EVDJob.VECTORS : EVDJob.NO_VECTORS, eig.uplo, n, eig.A, eig.ld, FloatBuffer.wrap(w));
            if (info != 0) {
                logger.error("LAPACK SYEV error code: {}", info);
                throw new ArithmeticException("LAPACK SYEV error code: " + info);
            }
            return new EVD(w, vr ? eig : null);
        } else {
            float[] wr = new float[n];
            float[] wi = new float[n];
            FloatMatrix Vl = vl ? new FloatMatrix(n, n) : new FloatMatrix(1, 1);
            FloatMatrix Vr = vr ? new FloatMatrix(n, n) : new FloatMatrix(1, 1);
            int info = LAPACK.engine.geev(eig.layout(), vl ? EVDJob.VECTORS : EVDJob.NO_VECTORS, vr ? EVDJob.VECTORS : EVDJob.NO_VECTORS, n, eig.A, eig.ld, FloatBuffer.wrap(wr), FloatBuffer.wrap(wi), Vl.A, Vl.ld, Vr.A, Vr.ld);
            if (info != 0) {
                logger.error("LAPACK GEEV error code: {}", info);
                throw new ArithmeticException("LAPACK GEEV error code: " + info);
            }

            float[] w = new float[2 * n];
            System.arraycopy(wr, 0, w, 0, n);
            System.arraycopy(wi, 0, w, n, n);
            return new EVD(wr, wi, vl ? Vl : null, vr ? Vr : null);
        }
    }

    /**
     * Singular Value Decomposition.
     * <p>
     * For an m-by-n matrix A with m &ge; n, the singular value decomposition is
     * an m-by-n orthogonal matrix U, an n-by-n diagonal matrix &Sigma;, and
     * an n-by-n orthogonal matrix V so that A = U*&Sigma;*V'.
     * <p>
     * For m &lt; n, only the first m columns of V are computed and &Sigma; is m-by-m.
     * <p>
     * The singular values, &sigma;<sub>k</sub> = &Sigma;<sub>kk</sub>, are ordered
     * so that &sigma;<sub>0</sub> &ge; &sigma;<sub>1</sub> &ge; ... &ge; &sigma;<sub>n-1</sub>.
     * <p>
     * The singular value decomposition always exists. The matrix condition number
     * and the effective numerical rank can be computed from this decomposition.
     * <p>
     * SVD is a very powerful technique for dealing with sets of equations or matrices
     * that are either singular or else numerically very close to singular. In many
     * cases where Gaussian elimination and LU decomposition fail to give satisfactory
     * results, SVD will diagnose precisely what the problem is. SVD is also the
     * method of choice for solving most linear least squares problems.
     * <p>
     * Applications which employ the SVD include computing the pseudo-inverse, least
     * squares fitting of data, matrix approximation, and determining the rank,
     * range and null space of a matrix. The SVD is also applied extensively to
     * the study of linear inverse problems, and is useful in the analysis of
     * regularization methods such as that of Tikhonov. It is widely used in
     * statistics where it is related to principal component analysis. Yet another
     * usage is latent semantic indexing in natural language text processing.
     *
     * @author Haifeng Li
     */
    public static class SVD {

        /**
         * The singular values in descending order.
         */
        public final float[] s;
        /**
         * The left singular vectors U.
         */
        public final FloatMatrix U;
        /**
         * The right singular vectors V.
         */
        public final FloatMatrix V;

        /**
         * Constructor.
         */
        public SVD(float[] s, FloatMatrix U, FloatMatrix V) {
            this.s = s;
            this.U = U;
            this.V = V;
        }
    }

    /**
     * Eigenvalue decomposition. Eigen decomposition is the factorization
     * of a matrix into a canonical form, whereby the matrix is represented in terms
     * of its eigenvalues and eigenvectors:
     * <p>
     * <pre><code>
     *     A = V*D*V<sup>-1</sup>
     * </code></pre>
     * If A is symmetric, then A = V*D*V' where the eigenvalue matrix D is
     * diagonal and the eigenvector matrix V is orthogonal.
     * <p>
     * Given a linear transformation A, a non-zero vector x is defined to be an
     * eigenvector of the transformation if it satisfies the eigenvalue equation
     * <p>
     * <pre><code>
     *     A x = &lambda; x
     * </code></pre>
     * for some scalar &lambda;. In this situation, the scalar &lambda; is called
     * an eigenvalue of A corresponding to the eigenvector x.
     * <p>
     * The word eigenvector formally refers to the right eigenvector, which is
     * defined by the above eigenvalue equation A x = &lambda; x, and is the most
     * commonly used eigenvector. However, the left eigenvector exists as well, and
     * is defined by x A = &lambda; x.
     * <p>
     * Let A be a real n-by-n matrix with strictly positive entries a<sub>ij</sub>
     * &gt; 0. Then the following statements hold.
     * <ol>
     * <li> There is a positive real number r, called the Perron-Frobenius
     * eigenvalue, such that r is an eigenvalue of A and any other eigenvalue &lambda;
     * (possibly complex) is strictly smaller than r in absolute value,
     * |&lambda;| &lt; r.
     * <li> The Perron-Frobenius eigenvalue is simple: r is a simple root of the
     *      characteristic polynomial of A. Consequently, both the right and the left
     *      eigenspace associated to r is one-dimensional.
     * </li>
     * <li> There exists a left eigenvector v of A associated with r (row vector)
     *      having strictly positive components. Likewise, there exists a right
     *      eigenvector w associated with r (column vector) having strictly positive
     *      components.
     * </li>
     * <li> The left eigenvector v (respectively right w) associated with r, is the
     *      only eigenvector which has positive components, i.e. for all other
     *      eigenvectors of A there exists a component which is not positive.
     * </li>
     * </ol>
     * <p>
     * A stochastic matrix, probability matrix, or transition matrix is used to
     * describe the transitions of a Markov chain. A right stochastic matrix is
     * a square matrix each of whose rows consists of nonnegative real numbers,
     * with each row summing to 1. A left stochastic matrix is a square matrix
     * whose columns consist of nonnegative real numbers whose sum is 1. A doubly
     * stochastic matrix where all entries are nonnegative and all rows and all
     * columns sum to 1. A stationary probability vector &pi; is defined as a
     * vector that does not change under application of the transition matrix;
     * that is, it is defined as a left eigenvector of the probability matrix,
     * associated with eigenvalue 1: &pi;P = &pi;. The Perron-Frobenius theorem
     * ensures that such a vector exists, and that the largest eigenvalue
     * associated with a stochastic matrix is always 1. For a matrix with strictly
     * positive entries, this vector is unique. In general, however, there may be
     * several such vectors.
     *
     * @author Haifeng Li
     */
    public static class EVD {
        /**
         * The real part of eigenvalues.
         * By default the eigenvalues and eigenvectors are not always in
         * sorted order. The <code>sort</code> function puts the eigenvalues
         * in descending order and reorder the corresponding eigenvectors.
         */
        public final float[] wr;
        /**
         * The imaginary part of eigenvalues.
         */
        public final float[] wi;
        /**
         * The left eigenvectors.
         */
        public final FloatMatrix Vl;
        /**
         * The right eigenvectors.
         */
        public final FloatMatrix Vr;

        /**
         * Constructor.
         *
         * @param w eigenvalues.
         * @param V eigenvectors.
         */
        public EVD(float[] w, FloatMatrix V) {
            this.wr = w;
            this.wi = null;
            this.Vl = V;
            this.Vr = V;
        }

        /**
         * Constructor.
         *
         * @param wr the real part of eigenvalues.
         * @param wi the imaginary part of eigenvalues.
         * @param Vl the left eigenvectors.
         * @param Vr the right eigenvectors.
         */
        public EVD(float[] wr, float[] wi, FloatMatrix Vl, FloatMatrix Vr) {
            this.wr = wr;
            this.wi = wi;
            this.Vl = Vl;
            this.Vr = Vr;
        }

        /**
         * Returns the block diagonal eigenvalue matrix whose diagonal are the real
         * part of eigenvalues, lower subdiagonal are positive imaginary parts, and
         * upper subdiagonal are negative imaginary parts.
         */
        public FloatMatrix diag() {
            FloatMatrix D = FloatMatrix.diag(wr);

            if (wi != null) {
                int n = wr.length;
                for (int i = 0; i < n; i++) {
                    if (wi[i] > 0) {
                        D.set(i, i + 1, wi[i]);
                    } else if (wi[i] < 0) {
                        D.set(i, i - 1, wi[i]);
                    }
                }
            }

            return D;
        }

        /**
         * Sorts the eigenvalues in descending order and reorders the
         * corresponding eigenvectors.
         */
        public EVD sort() {
            int n = wr.length;
            float[] w = new float[n];
            if (wi != null) {
                for (int i = 0; i < n; i++) {
                    w[i] = -(wr[i] * wr[i] + wi[i] * wi[i]);
                }
            } else {
                for (int i = 0; i < n; i++) {
                    w[i] = -(wr[i] * wr[i]);
                }
            }

            int[] index = QuickSort.sort(w);
            float[] wr2 = new float[n];
            for (int j = 0; j < n; j++) {
                wr2[j] = wr[index[j]];
            }

            float[] wi2 = null;
            if (wi != null) {
                wi2 = new float[n];
                for (int j = 0; j < n; j++) {
                    wi2[j] = wi[index[j]];
                }
            }

            FloatMatrix Vl2 = null;
            if (Vl != null) {
                Vl2 = new FloatMatrix(n, n);
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        Vl2.set(i, j, Vl.get(i, index[j]));
                    }
                }
            }

            FloatMatrix Vr2 = null;
            if (Vr != null) {
                Vr2 = new FloatMatrix(n, n);
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        Vr2.set(i, j, Vr.get(i, index[j]));
                    }
                }

            }

            return new EVD(wr2, wi2, Vl2, Vr2);
        }
    }

    /**
     * The LU decomposition. For an m-by-n matrix A with m &ge; n, the LU
     * decomposition is an m-by-n unit lower triangular matrix L, an n-by-n
     * upper triangular matrix U, and a permutation vector piv of length m
     * so that A(piv,:) = L*U. If m &lt; n, then L is m-by-m and U is m-by-n.
     * <p>
     * The LU decomposition with pivoting always exists, even if the matrix is
     * singular. The primary use of the LU decomposition is in the solution of
     * square systems of simultaneous linear equations if it is not singular.
     * The decomposition can also be used to calculate the determinant.
     *
     * @author Haifeng Li
     */
    public static class LU {
        /**
         * Array for internal storage of decomposition.
         */
        public final FloatMatrix lu;

        /**
         * Internal storage of pivot vector.
         */
        public final int[] ipiv;

        /**
         * If info = 0, the LU decomposition was successful.
         * If info = i > 0,  U(i,i) is exactly zero. The factorization
         * has been completed, but the factor U is exactly
         * singular, and division by zero will occur if it is used
         * to solve a system of equations.
         */
        public final int info;

        /**
         * Constructor.
         * @param lu       LU decomposition matrix
         * @param ipiv     the pivot vector
         * @param info     info > 0 if the matrix is singular
         */
        public LU(FloatMatrix lu, int[] ipiv, int info) {
            this.lu = lu;
            this.ipiv = ipiv;
            this.info = info;
        }

        /**
         * Returns if the matrix is singular.
         */
        public boolean isSingular() {
            return info > 0;
        }

        /**
         * Returns the matrix determinant
         */
        public float det() {
            int m = lu.m;
            int n = lu.n;

            if (m != n) {
                throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
            }

            float d = 1.0f;
            for (int j = 0; j < n; j++) {
                d *= lu.get(j, j);
            }

            for (int j = 0; j < n; j++){
                if (j+1 != ipiv[j]) {
                    d = -d;
                }
            }

            return d;
        }

        /**
         * Returns the matrix inverse. For pseudo inverse, use QRDecomposition.
         */
        public FloatMatrix inverse() {
            FloatMatrix inv = FloatMatrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solve A * x = b.
         * @param b  right hand side of linear system.
         *           On output, b will be overwritten with the solution matrix.
         * @exception  RuntimeException  if matrix is singular.
         */
        public float[] solve(float[] b) {
            float[] x = b.clone();
            solve(new FloatMatrix(x));
            return x;
        }

        /**
         * Solve A * X = B. B will be overwritten with the solution matrix on output.
         * @param B  right hand side of linear system.
         *           On output, B will be overwritten with the solution matrix.
         * @throws  RuntimeException  if matrix is singular.
         */
        public void solve(FloatMatrix B) {
            if (lu.m != lu.n) {
                throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", lu.m, lu.n));
            }

            if (B.m != lu.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.m, lu.n, B.m, B.n));
            }

            if (lu.layout() != B.layout()) {
                throw new IllegalArgumentException("The matrix layout is inconsistent.");
            }

            if (info > 0) {
                throw new RuntimeException("The matrix is singular.");
            }

            int ret = LAPACK.engine.getrs(lu.layout(), Transpose.NO_TRANSPOSE, lu.n, B.n, lu.A, lu.ld, IntBuffer.wrap(ipiv), B.A, B.ld);
            if (ret != 0) {
                logger.error("LAPACK GETRS error code: {}", ret);
                throw new ArithmeticException("LAPACK GETRS error code: " + ret);
            }
        }
    }

    /**
     * The Cholesky decomposition of a symmetric, positive-definite matrix.
     * When it is applicable, the Cholesky decomposition is roughly twice as
     * efficient as the LU decomposition for solving systems of linear equations.
     * <p>
     * The Cholesky decomposition is mainly used for the numerical solution of
     * linear equations. The Cholesky decomposition is also commonly used in
     * the Monte Carlo method for simulating systems with multiple correlated
     * variables: The matrix of inter-variable correlations is decomposed,
     * to give the lower-triangular L. Applying this to a vector of uncorrelated
     * simulated shocks, u, produces a shock vector Lu with the covariance
     * properties of the system being modeled.
     * <p>
     * Unscented Kalman filters commonly use the Cholesky decomposition to choose
     * a set of so-called sigma points. The Kalman filter tracks the average
     * state of a system as a vector x of length n and covariance as an n-by-n
     * matrix P. The matrix P is always positive semi-definite, and can be
     * decomposed into L*L'. The columns of L can be added and subtracted from
     * the mean x to form a set of 2n vectors called sigma points. These sigma
     * points completely capture the mean and covariance of the system state.
     *
     * @author Haifeng Li
     */
    public static class Cholesky {

        /**
         * The Cholesky decomposition.
         */
        public final FloatMatrix lu;

        /**
         * Constructor.
         * @param lu the lower/upper triangular part of matrix contains the Cholesky
         *           factorization.
         */
        public Cholesky(FloatMatrix lu) {
            if (lu.nrows() != lu.ncols()) {
                throw new UnsupportedOperationException("Cholesky constructor on a non-square matrix");
            }
            this.lu = lu;
        }

        /**
         * Returns the matrix determinant
         */
        public float det() {
            float d = 1.0f;
            for (int i = 0; i < lu.n; i++) {
                d *= lu.get(i, i);
            }

            return d * d;
        }

        /**
         * Returns the matrix inverse.
         */
        public FloatMatrix inverse() {
            FloatMatrix inv = FloatMatrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solve the linear system A * x = b.
         * @param b the right hand side of linear systems.
         * @return the solution vector.
         */
        public float[] solve(float[] b) {
            float[] x = b.clone();
            solve(new FloatMatrix(x));
            return x;
        }

        /**
         * Solve the linear system A * X = B.
         * @param B the right hand side of linear systems. On output, B will
         *          be overwritten with the solution matrix.
         */
        public void solve(FloatMatrix B) {
            if (B.m != lu.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.m, lu.n, B.m, B.n));
            }

            int info = LAPACK.engine.potrs(lu.layout(), lu.uplo, lu.n, B.n, lu.A, lu.ld, B.A, B.ld);
            if (info != 0) {
                logger.error("LAPACK POTRS error code: {}", info);
                throw new ArithmeticException("LAPACK POTRS error code: " + info);
            }
        }
    }

    /**
     * The QR decomposition. For an m-by-n matrix A with m &ge; n,
     * the QR decomposition is an m-by-n orthogonal matrix Q and
     * an n-by-n upper triangular matrix R such that A = Q*R.
     * <p>
     * The QR decomposition always exists, even if the matrix does not have
     * full rank. The primary use of the QR decomposition is in the least squares
     * solution of non-square systems of simultaneous linear equations.
     *
     * @author Haifeng Li
     */
    public static class QR {
        /**
         * The QR decomposition.
         */
        public final FloatMatrix qr;
        /**
         * The scalar factors of the elementary reflectors
         */
        public final float[] tau;

        /**
         * Constructor.
         */
        public QR(FloatMatrix qr, float[] tau) {
            this.qr = qr;
            this.tau = tau;
        }

        /**
         * Returns the Cholesky decomposition of A'A.
         */
        public Cholesky CholeskyOfAtA() {
            int n = qr.n;
            FloatMatrix L = FloatMatrix.diag(tau);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < i; j++) {
                    L.set(i, j, qr.get(j, i));
                }
            }

            L.uplo(UPLO.LOWER);
            return new Cholesky(L);
        }

        /**
         * Returns the upper triangular factor.
         */
        public FloatMatrix R() {
            int n = qr.n;
            FloatMatrix R = FloatMatrix.diag(tau);
            for (int i = 0; i < n; i++) {
                for (int j = i+1; j < n; j++) {
                    R.set(i, j, qr.get(i, j));
                }
            }

            return R;
        }

        /**
         * Returns the orthogonal factor.
         */
        public FloatMatrix Q() {
            int m = qr.m;
            int n = qr.n;
            FloatMatrix Q = new FloatMatrix(m, n);
            for (int k = n - 1; k >= 0; k--) {
                Q.set(k, k, 1.0f);
                for (int j = k; j < n; j++) {
                    if (qr.get(k, k) != 0) {
                        float s = 0.0f;
                        for (int i = k; i < m; i++) {
                            s += qr.get(i, k) * Q.get(i, j);
                        }
                        s = -s / qr.get(k, k);
                        for (int i = k; i < m; i++) {
                            Q.add(i, j, s * qr.get(i, k));
                        }
                    }
                }
            }
            return Q;
        }

        /**
         * Solve the least squares min || B - A*X ||.
         * @param b  the right hand side of overdetermined linear system.
         * @return   the solution vector beta that minimizes ||Y - X*beta||.
         * @exception  RuntimeException if matrix is rank deficient.
         */
        public float[] solve(float[] b) {
            if (b.length != qr.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", qr.m, qr.n, b.length));
            }

            if (b.length != qr.n) {
                throw new IllegalArgumentException("A and x dimensions don't match.");
            }

            float[] y = b.clone();
            solve(new FloatMatrix(y));
            float[] x = new float[qr.n];
            System.arraycopy(y, 0, x, 0, x.length);
            return x;
        }

        /**
         * Solve the least squares min || B - A*X ||.
         * @param B the right hand side of overdetermined linear system.
         *          B will be overwritten with the solution matrix on output.
         * @exception  RuntimeException if matrix is rank deficient.
         */
        public void solve(FloatMatrix B) {
            if (B.m != qr.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrows(), qr.nrows(), B.nrows(), B.ncols()));
            }

            int m = qr.m;
            int n = qr.n;
            int k = Math.min(m, n);

            int info = LAPACK.engine.ormqr(qr.layout(), Side.LEFT, Transpose.TRANSPOSE, B.nrows(), B.ncols(), k, qr.A, qr.ld, FloatBuffer.wrap(tau), B.A, B.ld);
            if (info != 0) {
                logger.error("LAPACK ORMQR error code: {}", info);
                throw new IllegalArgumentException("LAPACK ORMQR error code: " + info);
            }

            info = LAPACK.engine.trtrs(qr.layout(), UPLO.UPPER, Transpose.NO_TRANSPOSE, Diag.NON_UNIT, qr.n, B.n, qr.A, qr.ld, B.A, B.ld);

            if (info != 0) {
                logger.error("LAPACK TRTRS error code: {}", info);
                throw new IllegalArgumentException("LAPACK TRTRS error code: " + info);
            }
        }
    }
}
