/*
 * Software by Erich Bremer
 * ALL RIGHTS RESERVED
 */

package com.ebremer.halcyon.imagebox;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.ByteArrayHandle;
import loci.common.IRandomAccess;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;

/**
 *
 * @author erich
 */
public class HTTPIRandomAccess implements IRandomAccess {
    private final String url;
    private HttpClient httpClient = null;
    private long length = -1;
    public long chunksize = (long) Math.pow(2,18);
    private long pos;
    private ByteArrayHandle bah;
    private final TreeMap<Integer,ByteArrayHandle> tm;
    private final String uuid = UUID.randomUUID().toString();
    private int CurrentChunk = -1;
    private ByteOrder endianess = ByteOrder.LITTLE_ENDIAN;
    private String cookie = null;
    
    public HTTPIRandomAccess(String url) {
        //System.out.println("HTTPIRandomAccess4 " +url);
        tm = new TreeMap<>();
        this.url = url;
    }
    
    public void init() {
        if (httpClient == null) {
            //SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
            //sslContextFactory.addExcludeProtocols("TLSv1", "TLSv1.1");
            httpClient = new HttpClient();
            httpClient.setFollowRedirects(true);
            httpClient.setMaxConnectionsPerDestination(50);
            try {
                httpClient.start();
            } catch (Exception ex) {
                Logger.getLogger(HTTPIRandomAccess.class.getName()).log(Level.SEVERE, null, ex);
            }
            String mimetype = "application/octet-stream";
            InputStreamResponseListener listener = new InputStreamResponseListener();
            Request request = httpClient.newRequest(this.url)
                    .method(HttpMethod.HEAD)
                    .header("Accept", mimetype);
            if (cookie!=null) {
                request.header(HttpHeader.COOKIE, cookie);
            }
            request.send(listener);
            Response response = null;
            try {
                response = listener.get(240, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException | ExecutionException ex) {
                Logger.getLogger(HTTPIRandomAccess.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (response.getStatus() == 200) {
                this.length = response.getHeaders().getField(HttpHeader.CONTENT_LENGTH).getLongValue();
                try {
                    seek(0L);
                    endianess = bah.getOrder();
                } catch (IOException ex) {
                    Logger.getLogger(HTTPIRandomAccess.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                System.out.println("zamError detected on accessing!!! : ("+response.getStatus()+") : "+url);
            }
        }        
    }
    
    public void setCookie(String cookie) {
        this.cookie = cookie;
    }
    
    private int WhatChunkIs(long address) {
        return (int) (address/chunksize);
    }
    
    private void GrabChunk(int chunk) {
        //System.out.println("Grabbing chunk : "+chunk);
        if (tm.containsKey(chunk)) {
           // System.out.println("CACHE HIT! "+chunk);
            bah = tm.get(chunk);
        } else {
            FillBuffer(chunk*chunksize,chunksize);
            tm.put(chunk, bah);
        }
        CurrentChunk = chunk;
    }
    
    private boolean InChunk(long address) {
        return (CurrentChunk==WhatChunkIs(address));
    }
    
    private void FillBuffer(long start, long len) {
        //System.out.println("xFillBuffer   start "+Long.toHexString(start)+ " end "+Long.toHexString(start+len));
        byte[] bytes = null;
        String mimetype = "application/octet-stream";
        InputStreamResponseListener listener = new InputStreamResponseListener();
        long b = start+len-1;
        if (b>this.length) {
            b = this.length;
        }
        Request request = httpClient.newRequest(this.url).method(HttpMethod.GET).header("Accept", mimetype).header(HttpHeader.RANGE, "bytes="+start+"-"+b);
        if (cookie!=null) {
            request.header(HttpHeader.COOKIE, cookie);
        }
        request.send(listener);
        Response response = null;
        try {
            response = listener.get(240, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException ex) {
            Logger.getLogger(HTTPIRandomAccess.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (response.getStatus() == 206) {
            try (InputStream responseContent = listener.getInputStream()) {
                bytes = new byte[(int)len];
                for (int z=0; z<len;z++) {
                    bytes[z] = (byte) responseContent.read();
                }
            } catch (IOException ex) {
                Logger.getLogger(HTTPIRandomAccess.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println("COOKIE :\n"+cookie);
            } finally {
  
            }
        } else {
            System.out.println(this.uuid+"   dahaError detected on accessing : ("+response.getStatus()+") : "+url);
        }
        bah = new ByteArrayHandle(ByteBuffer.wrap(bytes));
        bah.setOrder(endianess);
        pos = start;
    }

    @Override
    public void close() throws IOException {
        //throw new UnsupportedOperationException("Why am I closing"); 
        System.out.println("close() I'm a gonna do nothing....");
    }
    	

    @Override
    public long getFilePointer() throws IOException {
        //System.out.println("getFilePointer()");
        return this.pos;
    }

    @Override
    public long length() throws IOException {
        //System.out.println("length() = "+this.length);
        if (length<0) {
            throw new IOException("length is negative!!!");
        }
        return this.length;
    }

    @Override
    public ByteOrder getOrder() {
        //System.out.println("getOrder : "+bah2.getOrder());
        //return bah.getOrder();
        return this.endianess;
    }

    @Override
    public void setOrder(ByteOrder order) {
    //    System.out.println("setOrder() = "+order);
        this.endianess = order;
    }

    @Override
    public int read(byte[] b) throws IOException {
        for (int i=0;i<b.length;i++) {
            b[i] = this.readByte();
        }
        return b.length;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {      
        //System.out.println(pos +" "+length+" READ "+b.length+" "+off+" "+len);
        //numreadByte++;
        int i = 0;
        while (i<len) {
            b[i+off] = this.readByte();
            i++;
        }
        return i;
    }

    public void Validate(long pos) throws IOException {
        //System.out.println("> Validate("+Long.toHexString(pos)+")");
        if ((pos > length)&&(pos<0)) { throw new IOException("Outside of valid range"); }
        if (!InChunk(pos)) {
            GrabChunk(WhatChunkIs(pos));
        }
        this.pos = pos;
        bah.seek(pos%chunksize);
    }
    
    
    @Override
    public void seek(long pos) throws IOException {
        //System.out.println("> seek("+Long.toHexString(pos)+")");
        if ((pos > length)&&(pos<0)) { throw new IOException("Outside of valid range"); }
        if (!InChunk(pos)) {
            GrabChunk(WhatChunkIs(pos));
        }
        this.pos = pos;
        bah.seek(pos%chunksize);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        //System.out.println("skipBytes(int n) = "+n);
        seek(pos+n);
        return n;
    }
    
    @Override
    public long skipBytes(long l) throws IOException {
        //System.out.println("skipBytes(long n) = "+l);
        seek(pos+l);
        return l;
    }  

    @Override
    public byte readByte() throws IOException {
        int ch = this.read0();
        //if (ch < 0) throw new EOFException();
        return (byte)(ch);
    }
    
    public int read0() {
        if (this.pos<this.length) {
            try {
                Validate(this.pos);
            } catch (IOException ex) {
                return -1;
            }
            byte b;
            try {
                b = bah.readByte();
                this.pos++;
            } catch (IOException ex) {
                return -1;
            }
            //int wow = b;
            //return b;
            //System.out.println(Byte.valueOf(b).toString()+"  "+Integer.toHexString(wow)+"  "+Integer.toHexString(0x00ff&b));
            return 0x00ff&b;
        }
        return -1;
    }    

    @Override
    public short readShort() throws IOException {
        //System.out.println("readShort() " + this.getOrder());
        int ch1 = this.read0();
        int ch2 = this.read0();
        if ((ch1 | ch2) < 0) throw new EOFException();
        if (this.getOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (short)((ch2 << 8) + ch1);
        } else {
            return (short)((ch1 << 8) + ch2);
        }
    }

    @Override
    public int readUnsignedShort() throws IOException {
        int ch1 = this.read0();
        int ch2 = this.read0();
        if ((ch1 | ch2) < 0) throw new EOFException();
        if (this.getOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (ch2 << 8) + ch1;
        } else {
            return (ch1 << 8) + ch2;
        }
    }

    @Override
    public int readInt() throws IOException {
        int ch1 = this.read0();
        int ch2 = this.read0();
        int ch3 = this.read0();
        int ch4 = this.read0();
        if ((ch1 | ch2 | ch3 | ch4) < 0) throw new EOFException();
        if (this.getOrder() == ByteOrder.LITTLE_ENDIAN) {
            return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + ch1);
        } else {
            return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
        }
    }

    @Override
    public float readFloat() throws IOException {
        //System.out.println("readFloat()");
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public long readLong() throws IOException {
        long a = this.readInt();
        long b = this.readInt();
        if (this.getOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (b << 32) + (a & 0xFFFFFFFFL);
        } else {
            return (a << 32) + (b & 0xFFFFFFFFL);
        }
    }
    
    @Override
    public void readFully(byte[] b) throws IOException {
        int i = 0;
        while (i<b.length) {
            b[i] = this.readByte();
            i++;
        }
    }
    
    @Override
    public boolean exists() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public int read(ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int read(ByteBuffer buffer, int offset, int len) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public boolean readBoolean() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
  
    @Override
    public void write(ByteBuffer buf) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void write(ByteBuffer buf, int off, int len) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void readFully(byte[] b, int off, int len) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public char readChar() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public int readUnsignedByte() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double readDouble() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String readLine() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String readUTF() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void write(int b) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void write(byte[] b) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeByte(int v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeShort(int v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeChar(int v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeInt(int v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeLong(long v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeFloat(float v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeDouble(double v) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeBytes(String s) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeChars(String s) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeUTF(String s) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}