package com.citacloud.springboot.contacloud.app.errors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.DisconnectedClientHelper;

/** Ends writes to a lost HTML client before Vaadin attempts a second error response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HtmlClientDisconnectFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(HtmlClientDisconnectFilter.class);

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String accept=request.getHeader("Accept");
        return !"GET".equals(request.getMethod()) || accept==null
            || !accept.toLowerCase(Locale.ROOT).contains("text/html");
    }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        chain.doFilter(request,new DisconnectionAwareResponse(response));
    }

    private static final class DisconnectionAwareResponse extends HttpServletResponseWrapper {
        private ServletOutputStream stream;
        private boolean disconnected;
        DisconnectionAwareResponse(HttpServletResponse response){super(response);}

        private void write(IoAction action)throws IOException {
            if(disconnected)return;
            try{action.run();}
            catch(IOException ex){
                String type=getContentType();
                if(type==null || !type.toLowerCase(Locale.ROOT).startsWith("text/html")
                        || !DisconnectedClientHelper.isClientDisconnectedException(ex))throw ex;
                disconnected=true;
                LOG.debug("event=HtmlClientDisconnected traceId={}",TraceContext.currentOrCreate());
            }
        }

        @Override public ServletOutputStream getOutputStream()throws IOException {
            if(stream==null){
                ServletOutputStream target=super.getOutputStream();
                stream=new ServletOutputStream(){
                    @Override public void write(int value)throws IOException{DisconnectionAwareResponse.this.write(()->target.write(value));}
                    @Override public void write(byte[] bytes,int offset,int length)throws IOException{DisconnectionAwareResponse.this.write(()->target.write(bytes,offset,length));}
                    @Override public void flush()throws IOException{DisconnectionAwareResponse.this.write(target::flush);}
                    @Override public void close()throws IOException{DisconnectionAwareResponse.this.write(target::close);}
                    @Override public boolean isReady(){return target.isReady();}
                    @Override public void setWriteListener(WriteListener listener){target.setWriteListener(listener);}
                };
            }
            return stream;
        }

        @Override public void flushBuffer()throws IOException{write(super::flushBuffer);}
    }

    @FunctionalInterface private interface IoAction {void run()throws IOException;}
}
