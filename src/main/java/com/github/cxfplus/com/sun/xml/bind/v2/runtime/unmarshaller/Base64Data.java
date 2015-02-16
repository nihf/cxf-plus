/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.github.cxfplus.com.sun.xml.bind.v2.runtime.unmarshaller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import com.github.cxfplus.com.sun.xml.bind.DatatypeConverterImpl;
import com.github.cxfplus.com.sun.xml.bind.v2.runtime.XMLSerializer;
import com.github.cxfplus.com.sun.xml.bind.v2.runtime.output.Pcdata;
import com.github.cxfplus.com.sun.xml.bind.v2.runtime.output.UTF8XmlOutput;
import com.github.cxfplus.com.sun.xml.bind.v2.util.ByteArrayOutputStreamEx;
import com.github.cxfplus.com.sun.istack.Nullable;

/**
 * Fed to unmarshaller when the 'text' data is actually
 * a virtual image of base64 encoding of the binary data
 * transferred on the wire.
 *
 * Used for the MTOM support.
 *
 * This object is mutable and the owner of this object can
 * reuse it with new data.
 *
 * Also used by the marshaller to write out the binary data
 * that could be possibly attached.
 *
 * @see XmlVisitor#text(CharSequence)
 * @see XMLSerializer#text(Pcdata,String)
 *
 * @author Kohsuke Kawaguchi
 */
public final class Base64Data extends Pcdata {

    // either dataHandler or (data,dataLen,mimeType?) must be present

    private DataHandler dataHandler;

    private byte[] data;
    /**
     * Length of the valid data in {@link #data}.
     */
    private int dataLen;
    /**
     * Optional MIME type of {@link #data}.
     *
     * Unused when {@link #dataHandler} is set.
     * Use {@link DataHandler#getContentType()} in that case.
     */
    private @Nullable String mimeType;

    /**
     * Fills in the data object by a portion of the byte[].
     *
     * @param len
     *      data[0] to data[len-1] are treated as the data.
     */
    public void set(byte[] data, int len, @Nullable String mimeType) {
        this.data = data;
        this.dataLen = len;
        this.dataHandler = null;
        this.mimeType = mimeType;
    }

    /**
     * Fills in the data object by the byte[] of the exact length.
     *
     * @param data
     *      this buffer may be owned directly by the unmarshaleld JAXB object.
     */
    public void set(byte[] data,@Nullable String mimeType) {
        set(data,data.length,mimeType);
    }

    /**
     * Fills in the data object by a {@link DataHandler}.
     */
    public void set(DataHandler data) {
        assert data!=null;
        this.dataHandler = data;
        this.data = null;
    }

    /**
     * Gets the raw data.
     */
    public DataHandler getDataHandler() {
        if(dataHandler==null) {
            dataHandler = new DataHandler(new DataSource() {
                public String getContentType() {
                    return getMimeType();
                }

                public InputStream getInputStream() {
                    return new ByteArrayInputStream(data,0,dataLen);
                }

                public String getName() {
                    return null;
                }

                public OutputStream getOutputStream() {
                    throw new UnsupportedOperationException();
                }
            });
        }

        return dataHandler;
    }

    /**
     * Gets the byte[] of the exact length.
     */
    public byte[] getExact() {
        get();
        if(dataLen!=data.length) {
            byte[] buf = new byte[dataLen];
            System.arraycopy(data,0,buf,0,dataLen);
            data = buf;
        }
        return data;
    }

    /**
     * Gets the data as an {@link InputStream}.
     */
    public InputStream getInputStream() throws IOException {
        if(dataHandler!=null)
            return dataHandler.getInputStream();
        else
            return new ByteArrayInputStream(data,0,dataLen);
    }

    /**
     * Returns false if this object only has {@link DataHandler} and therefore
     * {@link #get()} operation is likely going to be expensive.
     */
    public boolean hasData() {
        return data!=null;
    }

    /**
     * Gets the raw data. The size of the byte array maybe larger than the actual length.
     */
    public byte[] get() {
        if(data==null) {
            try {
                ByteArrayOutputStreamEx baos = new ByteArrayOutputStreamEx(1024);
                InputStream is = dataHandler.getDataSource().getInputStream();
                baos.readFrom(is);
                is.close();
                data = baos.getBuffer();
                dataLen = baos.size();
            } catch (IOException e) {
                // TODO: report the error to the unmarshaller
                dataLen = 0;    // recover by assuming length-0 data
            }
        }
        return data;
    }

    public int getDataLen() {
        return dataLen;
    }

    public String getMimeType() {
        if(mimeType==null)
            return "application/octet-stream";
        return mimeType;
    }

    /**
     * Gets the number of characters needed to represent
     * this binary data in the base64 encoding.
     */
    public int length() {
        // for each 3 bytes you use 4 chars
        // if the remainder is 1 or 2 there will be 4 more
        get();  // fill in the buffer if necessary
        return ((dataLen+2)/3)*4;
    }

    /**
     * Encode this binary data in the base64 encoding
     * and returns the character at the specified position.
     */
    public char charAt(int index) {
        // we assume that the length() method is called before this method
        // (otherwise how would the caller know that the index is valid?)
        // so we assume that the byte[] is already populated

        int offset = index%4;
        int base = (index/4)*3;

        byte b1,b2;

        switch(offset) {
        case 0:
            return DatatypeConverterImpl.encode(data[base]>>2);
        case 1:
            if(base+1<dataLen)
                b1 = data[base+1];
            else
                b1 = 0;
            return DatatypeConverterImpl.encode(
                        ((data[base]&0x3)<<4) |
                        ((b1>>4)&0xF));
        case 2:
            if(base+1<dataLen) {
                b1 = data[base+1];
                if(base+2<dataLen)
                    b2 = data[base+2];
                else
                    b2 = 0;

                return DatatypeConverterImpl.encode(
                            ((b1&0xF)<<2)|
                            ((b2>>6)&0x3));
            } else
                return '=';
        case 3:
            if(base+2<dataLen)
                return DatatypeConverterImpl.encode(data[base+2]&0x3F);
            else
                return '=';
        }

        throw new IllegalStateException();
    }

    /**
     * Internally this is only used to split a text to a list,
     * which doesn't happen that much for base64.
     * So this method should be smaller than faster.
     */
    public CharSequence subSequence(int start, int end) {
        StringBuilder buf = new StringBuilder();
        get();  // fill in the buffer if we haven't done so
        for( int i=start; i<end; i++ )
            buf.append(charAt(i));
        return buf;
    }

    /**
     * Returns the base64 encoded string of this data.
     */
    public String toString() {
        get();  // fill in the buffer
        return DatatypeConverterImpl._printBase64Binary(data, 0, dataLen);
    }

    public void writeTo(char[] buf, int start) {
        get();
        DatatypeConverterImpl._printBase64Binary(data, 0, dataLen, buf, start);
    }

    public void writeTo(UTF8XmlOutput output) throws IOException {
        // TODO: this is inefficient if the data source is note byte[] but DataHandler
        get();
        output.text(data,dataLen);
    }
}