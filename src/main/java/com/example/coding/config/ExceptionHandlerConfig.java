package com.example.coding.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.rmi.server.ExportException;

@RestControllerAdvice
public class ExceptionHandlerConfig {

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        return new ResponseEntity<>("I/O Error: "+ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

   /* @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGlobalException(Exception ex) {
        return new ResponseEntity<>("Error caught "+ex.getMessage(),HttpStatus.BAD_REQUEST);
    }*/

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGlobalException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Unexpected Error");
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setProperty("errorCode",ex.getCause());
        return problemDetail;
    }
}
