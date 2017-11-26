package gov.va.ascent.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
public class GitRepoUpdateControllerTest {

    @SuppressWarnings("rawtypes")
    @Mock
    private Appender mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> captorLoggingEvent;

    @InjectMocks
    GitRepoUpdateController gitRepoUpdateController = new GitRepoUpdateController();

    @Mock
    GitRepoUpdater gitRepoUpdater = new GitRepoUpdaterImpl();

    @Before
    public void setup() throws Exception {
        //set up logger for testing
        final Logger logger = (Logger) LoggerFactory.getLogger(GitRepoUpdateController.class);
        logger.addAppender(mockAppender);
        logger.setLevel(Level.INFO);
    }

    @Test
    public void testUpdateRepoNoException() throws Exception {
        gitRepoUpdateController.updateRepo();
        // verify
        verify(mockAppender, times(0)).doAppend(captorLoggingEvent.capture());
    }

    @Test
    public void testUpdateRepoIOException() throws Exception {
        doThrow(new IOException("IOException for test")).when(gitRepoUpdater).updateRepo();
        gitRepoUpdateController.updateRepo();
        // verify
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        final List<LoggingEvent> loggingEvents = captorLoggingEvent.getAllValues();
        assertThat(loggingEvents.get(0).getMessage(), is("Error occurred: "));
    }

}
