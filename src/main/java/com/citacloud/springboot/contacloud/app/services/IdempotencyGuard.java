package com.citacloud.springboot.contacloud.app.services;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** Mantiene serializada una clave idempotente hasta que finaliza su transacción. */
public final class IdempotencyGuard {
    private static final ReentrantLock[] LOCKS = new ReentrantLock[512];
    static { for (int i=0;i<LOCKS.length;i++) LOCKS[i]=new ReentrantLock(); }
    private IdempotencyGuard() { }
    public static void acquire(UUID tenant,UUID company,UUID key) {
        if(tenant==null||company==null||key==null)return;
        if(!TransactionSynchronizationManager.isSynchronizationActive())return;
        ReentrantLock lock=LOCKS[Math.floorMod((tenant.toString()+'|'+company+'|'+key).hashCode(),LOCKS.length)];
        lock.lock();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){lock.unlock();}
        });
    }
}
