package com.citacloud.springboot.contacloud.app.errors;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HtmlClientDisconnectFilterTest {
    final HtmlClientDisconnectFilter filter=new HtmlClientDisconnectFilter();
    MockHttpServletRequest request=new MockHttpServletRequest("GET","/facturacion");
    @BeforeEach void setup(){request.addHeader("Accept","text/html,application/xhtml+xml");}
    @AfterEach void clear(){org.slf4j.MDC.remove(TraceContext.MDC_KEY);}
    ServletOutputStream failing(IOException failure){return new ServletOutputStream(){
        public void write(int value)throws IOException{throw failure;}
        public boolean isReady(){return true;}public void setWriteListener(WriteListener listener){}
    };}
    MockHttpServletResponse response(IOException failure)throws Exception{
        var response=spy(new MockHttpServletResponse());doReturn(failing(failure)).when(response).getOutputStream();return response;
    }
    @Test void bootstrapAbortadoNoIntentaEnviarUnSegundoError()throws Exception{
        var response=response(new ClientAbortException(new IOException("Conexión anulada")));response.setContentType("text/html");response.setCommitted(true);
        filter.doFilter(request,response,(req,res)->{
            var http=(HttpServletResponse)res;http.setContentType("text/html;charset=UTF-8");
            try{http.getOutputStream().write(new byte[]{1,2,3});}
            catch(IOException ex){http.sendError(500);}
        });
        verify(response,never()).sendError(anyInt());verify(response,never()).sendError(anyInt(),anyString());
    }
    @Test void escribeHtmlNormalSinCambiarContenidoNiCabeceras()throws Exception{
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->{
            var http=(HttpServletResponse)res;http.setContentType("text/html");http.setHeader("X-Test","ok");http.getOutputStream().write("<html>ok</html>".getBytes());http.flushBuffer();
        });assertEquals("<html>ok</html>",response.getContentAsString());assertEquals("ok",response.getHeader("X-Test"));
    }
    @Test void conservaLosErroresDeEscrituraQueNoSonDesconexion()throws Exception{
        var failure=new IOException("Fallo interno de salida");var response=response(failure);
        assertSame(failure,assertThrows(IOException.class,()->filter.doFilter(request,response,(req,res)->{res.setContentType("text/html");res.getOutputStream().write(1);})));
    }
    @Test void noOcultaErroresDeAplicacionNiDeRespuestaYaEnviada()throws Exception{
        var failure=new IllegalStateException("Cannot call sendError() after the response has been committed");
        assertSame(failure,assertThrows(IllegalStateException.class,()->filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{throw failure;})));
        assertThrows(ServletException.class,()->filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{throw new ServletException("Fallo real");}));
    }
    @Test void noInterceptaPostNiSolicitudesJson()throws Exception{
        for(String method:new String[]{"POST","GET"}){
            var req=new MockHttpServletRequest(method,"/api/test");req.addHeader("Accept",method.equals("POST")?"text/html":"application/json");
            var response=new MockHttpServletResponse();filter.doFilter(req,response,(r,s)->assertSame(response,s));
        }
    }
    @Test void noOcultaDesconexionesDePdfOAJsonAunqueAcceptIncluyaHtml()throws Exception{
        var failure=new ClientAbortException("Aborted");var response=response(failure);
        assertSame(failure,assertThrows(IOException.class,()->filter.doFilter(request,response,(req,res)->{res.setContentType("application/pdf");res.getOutputStream().write(1);})));
    }
    @Test void despuesDeDesconectarNoSigueEscribiendoEnElSocket()throws Exception{
        var response=spy(new MockHttpServletResponse());var stream=mock(ServletOutputStream.class);doReturn(stream).when(response).getOutputStream();doThrow(new ClientAbortException("Aborted")).when(stream).write(anyInt());
        filter.doFilter(request,response,(req,res)->{res.setContentType("text/html");res.getOutputStream().write(1);res.getOutputStream().write(2);res.getOutputStream().flush();res.flushBuffer();});
        verify(stream,times(1)).write(anyInt());verify(stream,never()).flush();
    }
    @Test void conservaElTraceIdYLaLimpiezaDelContexto()throws Exception{
        var response=response(new ClientAbortException("Aborted"));
        new CorrelationIdFilter().doFilter(request,response,(req,res)->filter.doFilter(req,res,(r,s)->{
            assertNotNull(org.slf4j.MDC.get(TraceContext.MDC_KEY));s.setContentType("text/html");s.getOutputStream().write(1);
        }));
        assertNotNull(response.getHeader(TraceContext.HEADER));assertNull(org.slf4j.MDC.get(TraceContext.MDC_KEY));
    }
    @Test void reconoceDesconexionAlVaciarElBuffer()throws Exception{
        var response=spy(new MockHttpServletResponse());doThrow(new ClientAbortException("Aborted")).when(response).flushBuffer();
        assertDoesNotThrow(()->filter.doFilter(request,response,(req,res)->{res.setContentType("text/html");res.flushBuffer();}));
    }
}
