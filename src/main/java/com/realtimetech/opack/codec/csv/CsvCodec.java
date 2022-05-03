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

package com.realtimetech.opack.codec.csv;

import com.realtimetech.opack.codec.OpackCodec;
import com.realtimetech.opack.exception.EncodeException;
import com.realtimetech.opack.util.StringWriter;
import com.realtimetech.opack.util.UnsafeOpackValue;
import com.realtimetech.opack.util.structure.FastStack;
import com.realtimetech.opack.util.structure.NativeList;
import com.realtimetech.opack.value.OpackArray;
import com.realtimetech.opack.value.OpackObject;
import com.realtimetech.opack.value.OpackValue;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CsvCodec extends OpackCodec<String, Writer> {
    public final static class Builder { // Builder Pattern
        private int encodeStackInitialSize;
        private int encodeStringBufferSize;
        private int decodeStackInitialSize;

        private boolean allowOpackValueToKeyValue;  // whether OpackValue can act as opackObject map's key
        private boolean enableConvertCharacterToString; // whether to change character to string
        private boolean replaceNullWithEmptyString; // whether represent null value as empty string
        private boolean skipEmptyLines; // whether to skip empty lines
        private boolean useSpace;   // whether to use empty space between comma-separated elements
        private boolean useCarriageReturnSeparator; // whehther to use \r as line feed

        public Builder() {
            this.encodeStackInitialSize = 128;
            this.encodeStringBufferSize = 1024;
            this.decodeStackInitialSize = 128;
            this.replaceNullWithEmptyString = false;
            this.skipEmptyLines = false;
            this.useSpace = false;
            this.useCarriageReturnSeparator = false;    // Default linefeed is new line character
        }

        public void setEncodeStackInitialSize(int encodeStackInitialSize) {
            this.encodeStackInitialSize = encodeStackInitialSize;
        }

        public void setEncodeStringBufferSize(int encodeStringBufferSize) {
            this.encodeStringBufferSize = encodeStringBufferSize;
        }

        public void setDecodeStackInitialSize(int decodeStackInitialSize) {
            this.decodeStackInitialSize = decodeStackInitialSize;
        }

        public void setreplaceNullWithEmptyString(boolean replaceNullWithEmptyString) {     // alt + insert hotkey
            this.replaceNullWithEmptyString = replaceNullWithEmptyString;
        }

        public void setSkipEmptyLines(boolean skipEmptyLines) {
            this.skipEmptyLines = skipEmptyLines;
        }

        public void setEnableConvertCharacterToString(boolean enableConvertCharacterToString) {
            this.enableConvertCharacterToString = enableConvertCharacterToString;
        }

        public void setAllowOpackValueToKeyValue(boolean allowOpackValueToKeyValue) {
            this.allowOpackValueToKeyValue = allowOpackValueToKeyValue;
        }

        public void setUseSpace(boolean useSpace) {
            this.useSpace = useSpace;
        }

        public void setUseCarriageReturnSeparator(boolean useCarriageReturnSeparator) {
            this.useCarriageReturnSeparator = useCarriageReturnSeparator;
        }

        public CsvCodec create() {
            return new CsvCodec(this);
        }
    }

    private static final char[] CONST_U2028 = "\\u2028".toCharArray();
    private static final char[] CONST_U2029 = "\\u2029".toCharArray();


    private static final char[] CONST_NULL_CHARACTER = new char[]{'n', 'u', 'l', 'l'};
    private static final char[] CONST_TRUE_CHARACTER = new char[]{'t', 'r', 'u', 'e'};
    private static final char[] CONST_FALSE_CHARACTER = new char[]{'f', 'a', 'l', 's', 'e'};

    private static final char[] CONST_SPACE_CHARACTER = new char[' '];
    private static final char[] CONST_SEPARATOR_CHARACTER = new char[]{','};
    private static final char[] CONST_NEWLINE_CHARACTER = new char[]{'\n'};
    private static final char[] CONST_CARRIAGE_RETURN_CHARACTER = new char[]{'\r'};

    private static final char[] CONST_STRING_OPEN_CHARACTER = new char[]{'\"'};
    private static final char[] CONST_STRING_CLOSE_CHARACTER = new char[]{'\"'};
    private static final char[] CONST_INFINITY_CHARACTER = new char[]{'i', 'n', 'f'};
    private static final char[] CONST_NAN_CHARACTER = new char[]{'N', 'a', 'N'};

    private static final char[][] CONST_REPLACEMENT_CHARACTERS;

    static {
        CONST_REPLACEMENT_CHARACTERS = new char[128][];
        for (int i = 0; i <= 0x1F; i++) {
            CONST_REPLACEMENT_CHARACTERS[i] = String.format("\\u%04x", i).toCharArray();
        }
        CONST_REPLACEMENT_CHARACTERS['"'] = new char[]{'\\', '\"'};
        CONST_REPLACEMENT_CHARACTERS['\\'] = new char[]{'\\', '\\'};
        CONST_REPLACEMENT_CHARACTERS['\t'] = new char[]{'\\', 't'};
        CONST_REPLACEMENT_CHARACTERS['\b'] = new char[]{'\\', 'b'};
        CONST_REPLACEMENT_CHARACTERS['\n'] = new char[]{'\\', 'n'};
        CONST_REPLACEMENT_CHARACTERS['\r'] = new char[]{'\\', 'r'};
        CONST_REPLACEMENT_CHARACTERS['\f'] = new char[]{'\\', 'f'};
    }

    private final StringWriter encodeLiteralStringWriter;
    private final StringWriter encodeStringWriter;

    private final FastStack<Object> encodeStack;
    private final FastStack<Object> decodeStack;

    private final boolean allowOpackValueToKeyValue;
    private final boolean replaceNullWithEmptyString;
    private final boolean skipEmptyLines;
    private final boolean enableConvertCharacterToString;
    private final boolean useSpace;
    private final boolean useCarriageReturnSeparator;

    private CsvCodec(Builder builder) {
        super();

        this.encodeStack = new FastStack<>(builder.encodeStackInitialSize);
        this.decodeStack = new FastStack<>(builder.decodeStackInitialSize);

        this.encodeLiteralStringWriter = new StringWriter(builder.encodeStringBufferSize);
        this.encodeStringWriter = new StringWriter(builder.encodeStringBufferSize);

        this.allowOpackValueToKeyValue = builder.allowOpackValueToKeyValue;
        this.replaceNullWithEmptyString = builder.replaceNullWithEmptyString;
        this.skipEmptyLines = builder.skipEmptyLines;
        this.enableConvertCharacterToString = builder.enableConvertCharacterToString;
        this.useSpace = builder.useSpace;
        this.useCarriageReturnSeparator = builder.useCarriageReturnSeparator;
    }

    private boolean encodeLiteral(final Writer writer, Object object) throws IOException {
        if (object == null) {
            writer.write(CONST_NULL_CHARACTER);
            return true;
        }

        Class<?> objectType = object.getClass();

        if (objectType == OpackObject.class) {
            return false;
        } else if (objectType == OpackArray.class) {
            return false;
        } else if (objectType == String.class) {
            String string = (String) object;
            char[] charArray = string.toCharArray();

            writer.write(CONST_STRING_OPEN_CHARACTER);

            int last = 0;
            int length = charArray.length;

            for (int index = 0; index < length; index++) {
                char character = charArray[index];
                char[] replacement = null;

                if (character < CONST_REPLACEMENT_CHARACTERS.length) {
                    replacement = CONST_REPLACEMENT_CHARACTERS[character];
                } else if (character == '\u2028') {
                    replacement = CONST_U2028;
                } else if (character == '\u2029') {
                    replacement = CONST_U2029;
                }

                if (replacement != null) {  // big endian
                    if (last < index) {
                        writer.write(charArray, last, index - last);
                    }

                    writer.write(replacement);
                    last = index + 1;
                }
            }

            if (last < length) {
                writer.write(charArray, last, length - last);
            }

            writer.write(CONST_STRING_CLOSE_CHARACTER);
        } else {
            Class<?> numberType = objectType;

            writer.write(CONST_STRING_OPEN_CHARACTER);

            // Asserts
            if (numberType == Double.class) {
                Double doubleValue = (Double) object;

                if (Double.isNaN(doubleValue)) {
                    writer.write(CONST_NAN_CHARACTER);
                } else if (Double.isInfinite(doubleValue) || !Double.isFinite(doubleValue)) {
                    writer.write(CONST_INFINITY_CHARACTER);
                }
            } else if (numberType == Float.class) {
                Float floatValue = (Float) object;

                if (Float.isNaN(floatValue)) {
                    writer.write(CONST_NAN_CHARACTER);
                } else if (Float.isInfinite(floatValue) || !Float.isFinite(floatValue)) {
                    writer.write(CONST_INFINITY_CHARACTER);
                }
            }

            if (numberType == Character.class) {
                if (enableConvertCharacterToString) {
                    writer.write(CONST_STRING_OPEN_CHARACTER);
                    writer.write(object.toString());
                    writer.write(CONST_STRING_CLOSE_CHARACTER);
                } else {
                    writer.write(Integer.toString((char) object));
                }
            } else {
                writer.write(object.toString());
            }

            writer.write(CONST_STRING_CLOSE_CHARACTER);
        }

        return true;
    }

    private boolean encodeNativeArray(@NotNull Writer writer, @NotNull NativeList nativeList) throws IOException {
        Object arrayObject = nativeList.getArrayObject();
        Class<?> arrayType = arrayObject.getClass();

        if (arrayType == boolean[].class) {
            boolean[] array = (boolean[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                if (array[index]) {
                    writer.write(CONST_TRUE_CHARACTER);
                } else {
                    writer.write(CONST_FALSE_CHARACTER);
                }
            }

            return true;
        } else if (arrayType == byte[].class) {
            byte[] array = (byte[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                writer.write(Byte.toString(array[index]));
            }

            return true;
        } else if (arrayType == char[].class) {
            char[] array = (char[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                if (enableConvertCharacterToString) {
                    writer.write(CONST_STRING_OPEN_CHARACTER);
                    writer.write(Character.toString(array[index]));
                    writer.write(CONST_STRING_CLOSE_CHARACTER);
                } else {
                    writer.write(Integer.toString(array[index]));
                }
            }

            return true;
        } else if (arrayType == short[].class) {
            short[] array = (short[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                writer.write(Short.toString(array[index]));
            }

            return true;
        } else if (arrayType == int[].class) {
            int[] array = (int[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                writer.write(Integer.toString(array[index]));
            }

            return true;
        } else if (arrayType == float[].class) {
            float[] array = (float[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                writer.write(Float.toString(array[index]));
            }

            return true;
        } else if (arrayType == long[].class) {
            long[] array = (long[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                writer.write(Long.toString(array[index]));
            }

            return true;
        } else if (arrayType == double[].class) {
            double[] array = (double[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                writer.write(Double.toString(array[index]));
            }

            return true;
        } else if (arrayType == Boolean[].class) {
            Boolean[] array = (Boolean[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                if (array[index] == null) {
                    writer.write(CONST_NULL_CHARACTER);
                } else if (array[index]) {
                    writer.write(CONST_TRUE_CHARACTER);
                } else {
                    writer.write(CONST_FALSE_CHARACTER);
                }
            }

            return true;
        } else if (arrayType == Character[].class) {
            Character[] array = (Character[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                if (array[index] == null) {
                    writer.write(CONST_NULL_CHARACTER);
                } else {
                    if (enableConvertCharacterToString) {
                        writer.write(CONST_STRING_OPEN_CHARACTER);
                        writer.write(Character.toString(array[index]));
                        writer.write(CONST_STRING_CLOSE_CHARACTER);
                    } else {
                        writer.write(Integer.toString(array[index]));
                    }
                }
            }

            return true;
        } else if (arrayType == Byte[].class ||
                arrayType == Short[].class ||
                arrayType == Integer[].class ||
                arrayType == Long[].class) {
            Object[] array = (Object[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                if (array[index] == null) {
                    writer.write(CONST_NULL_CHARACTER);
                } else {
                    writer.write(array[index].toString());
                }
            }

            return true;
        } else if (arrayType == Float[].class) {
            Float[] array = (Float[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                if (array[index] == null) {
                    writer.write(CONST_NULL_CHARACTER);
                } else if (array[index].isNaN()) {
                    writer.write(CONST_NAN_CHARACTER);
                } else if (Float.isInfinite(array[index]) || !Float.isFinite(array[index])) {
                    writer.write(CONST_INFINITY_CHARACTER);
                } else {
                    writer.write(array[index].toString());
                }
            }

            return true;
        } else if (arrayType == Double[].class) {
            Double[] array = (Double[]) arrayObject;

            for (int index = 0; index < array.length; index++) {
                if (index != 0) {
                    writer.write(CONST_SEPARATOR_CHARACTER);
                }

                if (array[index] == null) {
                    writer.write(CONST_NULL_CHARACTER);
                } else if (array[index].isNaN()) {
                    writer.write(CONST_NAN_CHARACTER);
                } else if (Double.isInfinite(array[index]) || !Double.isFinite(array[index])) {
                    writer.write(CONST_INFINITY_CHARACTER);
                } else {
                    writer.write(array[index].toString());
                }
            }

            return true;
        }

        return false;
    }

    private enum possibleTypes {
        LITERAL,
        OPACKARRAY,
        OPACKOBJECT
    }

    @Override
    protected void doEncode(Writer writer, OpackValue opackValue) throws IOException {
        this.encodeLiteralStringWriter.reset();
        this.encodeStack.reset();

        this.encodeStack.push(opackValue);

        FastStack<Integer> depthStack = new FastStack<>();
        depthStack.push(0);

        FastStack<possibleTypes> typeStack = new FastStack<>();

        while (!this.encodeStack.isEmpty()) {

            Object object = this.encodeStack.pop();
            Class<?> objectType = object == null ? null : object.getClass();

            if (objectType == char[].class) {       // write const character immediately
                writer.write((char[]) object);
            } else if (objectType == OpackObject.class) {
                possibleTypes parentType = typeStack.getSize() != 0 ? typeStack.peek() : null;

                if (parentType == possibleTypes.OPACKOBJECT) {
                    throw new IllegalArgumentException("CSV cannot support nested structures.");
                }

                OpackObject<Object, Object> opackObject = (OpackObject<Object, Object>) object;
                Map<Object, Object> opackObjectMap = null;

                try {
                    opackObjectMap = UnsafeOpackValue.getMap(opackObject);
                } catch (InvocationTargetException | IllegalAccessException exception) {
                    throw new IOException("Can't access opack object map.", exception);
                }

                if (this.useCarriageReturnSeparator) {
                    this.encodeStack.push(CONST_CARRIAGE_RETURN_CHARACTER);
                } else {
                    this.encodeStack.push(CONST_NEWLINE_CHARACTER);
                }

                int index = 0;
                Set<Map.Entry<Object, Object>> entrySet = opackObjectMap.entrySet();
                int fieldCount = entrySet.size();

                int reverserStart = this.encodeStack.getSize();

                for (Map.Entry<Object, Object> entry : entrySet) {
                    Object key = entry.getKey();
                    Object value = entry.getValue();

                    if (!this.allowOpackValueToKeyValue && key instanceof OpackValue) {
                        throw new IllegalArgumentException("Object type keys are not allowed in csv format.");
                    }

                    if (value instanceof OpackValue) {
                        throw new IllegalArgumentException("CSV cannot support nested structures.");
                    }

                    this.encodeStack.push(key);

                    this.encodeStack.push(CONST_SEPARATOR_CHARACTER);

                    if (this.useSpace) {
                        this.encodeStack.push(CONST_SPACE_CHARACTER);
                    }

                    this.encodeStack.push(value);

                    // fields of opackObject is separated by linefeed
                    if (this.useCarriageReturnSeparator) {
                        this.encodeStack.push(CONST_CARRIAGE_RETURN_CHARACTER);
                    } else {
                        this.encodeStack.push(CONST_NEWLINE_CHARACTER);
                    }

                    index++;
                }

                this.encodeStack.reverse(reverserStart, this.encodeStack.getSize() - 1);
                typeStack.push(possibleTypes.OPACKOBJECT);

            } else if (objectType == OpackArray.class) {
                OpackArray<Object> opackArray = (OpackArray<Object>) object;
                List<Object> opackArrayList = null;

                try {
                    opackArrayList = UnsafeOpackValue.getList(opackArray);
                } catch (InvocationTargetException | IllegalAccessException exception) {
                    throw new IOException("Can't access opack array list.", exception);
                }

                possibleTypes parentType = typeStack.getSize() != 0 ? typeStack.peek() : null;

                int size = opackArray.length();
                int reverseStart = this.encodeStack.getSize();

                boolean optimized = false;

                if (opackArrayList instanceof NativeList) {
                    NativeList nativeList = (NativeList) opackArrayList;

                    optimized = encodeNativeArray(writer, nativeList);
                }

                if (!optimized) {
                    int index = 0;

                    for (Object arrayElement : opackArrayList) {
                        if (!this.encodeLiteral(this.encodeLiteralStringWriter, arrayElement)) {   // meet non-native target (OPACKARRAY, OPACKOBJECT)
                            if ( parentType == possibleTypes.OPACKARRAY || parentType == possibleTypes.OPACKOBJECT) {
                                throw new IllegalStateException("CSV cannot support nested structures.");
                            } else {
                                if(this.encodeLiteralStringWriter.getLength() > 0) {  //migrate
                                    this.encodeStack.push(this.encodeLiteralStringWriter.toCharArray());
                                    this.encodeStack.swap(this.encodeStack.getSize() - 1, this.encodeStack.getSize() - 2);
                                }
                            }

                            this.encodeLiteralStringWriter.reset();

                            if(index != size - 1) {
                                this.encodeStack.push(CONST_SEPARATOR_CHARACTER);

                                if (this.useSpace) {
                                    this.encodeStack.push(CONST_SPACE_CHARACTER);
                                }
                            } else {
                                if(parentType != null) {
                                    if(this.useCarriageReturnSeparator) {
                                        this.encodeStack.push(CONST_CARRIAGE_RETURN_CHARACTER);
                                    } else {
                                        this.encodeStack.push(CONST_NEWLINE_CHARACTER);
                                    }
                                }
                            }
                        } else {    // literal contents are written in encodeLiteralStringWriter
                            if (index != size - 1) {
                                this.encodeLiteralStringWriter.write(CONST_SEPARATOR_CHARACTER);

                                if (this.useSpace) {
                                    this.encodeLiteralStringWriter.write(CONST_SPACE_CHARACTER);
                                }
                            } else {
                                if (this.useCarriageReturnSeparator) {
                                    this.encodeLiteralStringWriter.write(CONST_CARRIAGE_RETURN_CHARACTER);
                                } else {
                                    this.encodeLiteralStringWriter.write(CONST_NEWLINE_CHARACTER);
                                }
                            }

                        }

                        index++;
                    }
                }

                if (this.encodeLiteralStringWriter.getLength() > 0) {
                    this.encodeStack.push(this.encodeLiteralStringWriter.toCharArray());
                    this.encodeLiteralStringWriter.reset();
                }

                this.encodeStack.reverse(reverseStart, this.encodeStack.getSize() - 1);

                typeStack.push(possibleTypes.OPACKARRAY);
            } else {
                this.encodeLiteral(writer, object);
            }
        }
    }

    public synchronized String encode(OpackValue opackValue) throws EncodeException {
        this.encodeStringWriter.reset();
        this.encode(this.encodeStringWriter, opackValue); // EncodeException happens here
        return this.encodeStringWriter.toString();
    }

    @Override
    protected OpackValue doDecode(String input) throws IOException {
        return null;
    }
}
