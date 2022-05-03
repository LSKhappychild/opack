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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.realtimetech.opack.Opacker;
import com.realtimetech.opack.codec.csv.CsvCodec;
import com.realtimetech.opack.codec.json.JsonCodec;
import com.realtimetech.opack.exception.DeserializeException;
import com.realtimetech.opack.exception.EncodeException;
import com.realtimetech.opack.exception.SerializeException;
import com.realtimetech.opack.test.codec.CommonOpackValue;
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

import java.io.SequenceInputStream;
import java.util.Arrays;

public class dummy {

    public static void main(String[] args) throws SerializeException, DeserializeException, JsonProcessingException, EncodeException {
        class Test{
            private String name = "Fuck you";
            private int one = 1;

            private int wtf(int b) {
                return b+1;
            }
        }

        Test a = new Test();


        int[] simpleInt = new int[]{1,2,3};
        String[] simpleString = new String[]{"a", "b", "c"};

        int[][] doubleInt = new int[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};

        Object[] test  = new Object[] {
          1, 3, "FUCK YOU", a
        };

        Opacker opacker = new Opacker.Builder().create();
        CsvCodec csvCodec = new CsvCodec.Builder().create();

        OpackValue inttest = opacker.serialize(simpleInt);
        OpackValue stringtest = opacker.serialize(simpleString);
        OpackValue doubleintlist = opacker.serialize(doubleInt);

        //OpackValue opackValue1 = opacker.serialize(test);

        String intCSV = csvCodec.encode(inttest);
        System.out.println(intCSV);

        String stringCSV = csvCodec.encode(stringtest);
        System.out.println(stringCSV);

        String doubleIntCSV = csvCodec.encode(doubleintlist);
        System.out.println(doubleIntCSV);
        //String complexCSV = csvCodec.encode(opackValue1);
        //System.out.println(complexCSV);

    }
}
