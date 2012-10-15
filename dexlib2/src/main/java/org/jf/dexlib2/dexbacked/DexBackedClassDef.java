/*
 * Copyright 2012, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.dexlib2.dexbacked;

import com.google.common.collect.ImmutableList;
import org.jf.dexlib2.DexFile;
import org.jf.dexlib2.DexFileReader;
import org.jf.dexlib2.dexbacked.util.*;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class DexBackedClassDef implements ClassDef {
    @Nonnull public final DexFile dexFile;

    @Nonnull public final String name;
    public final int accessFlags;
    @Nullable public final String superclass;
    @Nullable public final String sourceFile;

    @Nonnull private final AnnotationsDirectory annotationsDirectory;
    private final int staticInitialValuesOffset;

    private final int interfacesOffset;
    private final int classDataOffset;

    //class_def_item offsets
    private static final int ACCESS_FLAGS_OFFSET = 4;
    private static final int SUPERCLASS_OFFSET = 8;
    private static final int INTERFACES_OFFSET = 12;
    private static final int SOURCE_FILE_OFFSET = 16;
    private static final int ANNOTATIONS_OFFSET = 20;
    private static final int CLASS_DATA_OFFSET = 24;
    private static final int STATIC_INITIAL_VALUES_OFFSET = 28;

    public DexBackedClassDef(@Nonnull DexFile dexFile,
                             int classDefOffset) {
        this.dexFile = dexFile;

        this.name = dexFile.getType(dexFile.readSmallUint(classDefOffset));
        this.accessFlags = dexFile.readSmallUint(classDefOffset + ACCESS_FLAGS_OFFSET);
        this.superclass = dexFile.getOptionalString(dexFile.readSmallUint(classDefOffset + SUPERCLASS_OFFSET));
        this.interfacesOffset = dexFile.readSmallUint(classDefOffset + INTERFACES_OFFSET);
        this.sourceFile = dexFile.getOptionalString(dexFile.readSmallUint(classDefOffset + SOURCE_FILE_OFFSET));

        int annotationsDirectoryOffset = dexFile.readSmallUint(classDefOffset + ANNOTATIONS_OFFSET);
        this.annotationsDirectory = AnnotationsDirectory.newOrEmpty(dexFile, annotationsDirectoryOffset);

        this.classDataOffset = dexFile.readSmallUint(CLASS_DATA_OFFSET);
        this.staticInitialValuesOffset = dexFile.readSmallUint(classDefOffset + STATIC_INITIAL_VALUES_OFFSET);
    }


    @Nonnull @Override public String getName() { return name; }
    @Override public int getAccessFlags() { return accessFlags; }
    @Nullable @Override public String getSuperclass() { return superclass; }
    @Nullable @Override public String getSourceFile() { return sourceFile; }

    @Nonnull
    @Override
    public List<String> getInterfaces() {
        if (interfacesOffset > 0) {
            final int size = dexFile.readSmallUint(interfacesOffset);
            return new FixedSizeList<String>() {
                @Override
                public String readItem(int index) {
                    return dexFile.getString(dexFile.readSmallUint(interfacesOffset + 4 + (2*index)));
                }

                @Override public int size() { return size; }
            };
        }
        return ImmutableList.of();
    }

    @Nonnull
    @Override
    public List<? extends DexBackedAnnotation> getAnnotations() {
        return annotationsDirectory.getClassAnnotations();
    }

    @Nonnull
    @Override
    public List<? extends DexBackedField> getFields() {
        if (classDataOffset != 0) {
            DexFileReader reader = dexFile.readerAt(classDataOffset);
            int staticFieldCount = reader.readSmallUleb128();
            int instanceFieldCount = reader.readSmallUleb128();
            final int fieldCount = staticFieldCount + instanceFieldCount;
            if (fieldCount > 0) {
                reader.skipUleb128(); //direct_methods_size
                reader.skipUleb128(); //virtual_methods_size

                final int fieldsStartOffset = reader.getOffset();

                return new VariableSizeListWithContext<DexBackedField>() {
                    @Nonnull
                    @Override
                    public Iterator listIterator() {
                        return new Iterator(dexFile, fieldsStartOffset) {
                            private int previousFieldIndex = 0;
                            @Nonnull private final AnnotationsDirectory.AnnotationIterator annotationIterator =
                                    annotationsDirectory.getFieldAnnotationIterator();
                            @Nonnull private final StaticInitialValueIterator staticInitialValueIterator =
                                    StaticInitialValueIterator.newOrEmpty(dexFile, staticInitialValuesOffset);

                            @Nonnull
                            @Override
                            protected DexBackedField readItem(DexFileReader reader, int index) {
                                DexBackedField item = new DexBackedField(reader, previousFieldIndex,
                                        staticInitialValueIterator, annotationIterator);
                                previousFieldIndex = item.fieldIndex;
                                return item;
                            }

                            @Override
                            protected void skipItem(DexFileReader reader, int index) {
                                previousFieldIndex = DexBackedField.skipEncodedField(reader, previousFieldIndex);
                                staticInitialValueIterator.skipNext();
                            }
                        };
                    }

                    @Override public int size() { return fieldCount; }
                };
            }
        }
        return ImmutableList.of();
    }

    @Nonnull
    @Override
    public List<? extends Method> getMethods() {
        return null;
    }
}
