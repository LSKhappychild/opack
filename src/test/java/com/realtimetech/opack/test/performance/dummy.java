/*
 * Copyright (C) 2022 REALTIMETECH All Rights Reserved
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.realtimetech.opack.test.performance;

import com.realtimetech.opack.Opacker;
import com.realtimetech.opack.codec.json.JsonCodec;
import com.realtimetech.opack.exception.DeserializeException;
import com.realtimetech.opack.exception.SerializeException;
import com.realtimetech.opack.value.OpackArray;
import com.realtimetech.opack.value.OpackObject;
import com.realtimetech.opack.value.OpackValue;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.realtimetech.opack.Opacker;
import com.realtimetech.opack.codec.dense.DenseCodec;
import com.realtimetech.opack.codec.dense.writer.ByteArrayWriter;
import com.realtimetech.opack.test.opacker.array.PrimitiveArrayTest;
import com.realtimetech.opack.test.opacker.array.WrapperArrayTest;
import com.realtimetech.opack.test.opacker.single.ObjectTest;
import com.realtimetech.opack.test.opacker.single.PrimitiveTest;
import com.realtimetech.opack.test.opacker.single.StringTest;
import com.realtimetech.opack.test.opacker.single.WrapperTest;
import com.realtimetech.opack.value.OpackValue;
import com.fasterxml.jackson.dataformat.csv.*;

import java.util.Arrays;

public class dummy {

    public static void main(String[] args) throws SerializeException, DeserializeException {
        class Test{
            int one = 1;

            private int wtf(int b) {
                return b+1;
            }
        }

        JsonCodec sampleJson = new JsonCodec.Builder().create();

        Test a = new Test();
        Object[] test = new Object[]{a, 3, "Hello"};

        CsvSchema test =
    }
}
