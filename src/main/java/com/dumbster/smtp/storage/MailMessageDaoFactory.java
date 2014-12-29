package com.dumbster.smtp.storage;

import com.dumbster.smtp.app.ApplicationContextProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class MailMessageDaoFactory {

    private static volatile MailMessageDaoFactory instance;
    private static StorageMode storageMode;
    private MailMessageDao dao;

    public static enum StorageMode {
        SQLITE,
        FILE_BASED,
        IN_MEMORY,
    }


    private MailMessageDaoFactory() {
        ApplicationContext context = new ApplicationContextProvider().getApplicationContext();
        
        switch (storageMode) {
            case SQLITE:
                dao = context.getBean(SqliteMailMessageDao.class);
                break;
            case FILE_BASED:
                dao = context.getBean(FileBasedMailMessageDao.class);
                break;
            case IN_MEMORY:
                dao = context.getBean(InMemoryMailMessageDao.class);
                break;
            default:
                throw new IllegalStateException("Storage mode not initialized correctly.");
        }
    }

    public static MailMessageDaoFactory getInstance() {
        if (instance == null) {
            synchronized (MailMessageDaoFactory.class) {
                if (instance == null) {
                    instance = new MailMessageDaoFactory();
                }
            }
        }
        return instance;
    }

    public MailMessageDao getDao() {
        return this.dao;
    }

    public static void setStorageMode(StorageMode mode) {
        storageMode = mode;
    }
}
