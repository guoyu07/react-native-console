package com.github.beansoftapp.reatnative.idea.views;

import com.github.beansoftapp.reatnative.idea.utils.NotificationUtils;
import com.github.beansoftapp.reatnative.idea.utils.RNPathUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.actions.StopProcessAction;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ConsoleState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * A React Native Console, which can reuse window and execute commands.
 * Created by beansoft on 2017/5/25.
 */
public class RNConsoleImpl extends ConsoleViewImpl {

    private class RerunAction extends AnAction {
        public RerunAction() {
            super("Rerun", "Rerun",
                    AllIcons.Actions.Restart);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            reRun();
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setVisible(myGeneralCommandLine != null);
            e.getPresentation().setEnabled(myGeneralCommandLine != null);
            if(displayName != null) {
                e.getPresentation().setText("Rerun '" + displayName + "'");
                e.getPresentation().setDescription("Rerun '" + displayName + "'");
            } else if(myGeneralCommandLine != null) {
                e.getPresentation().setText("Rerun '" + myGeneralCommandLine.getCommandLineString() + "'");
                e.getPresentation().setDescription("Rerun '" + myGeneralCommandLine.getCommandLineString() + "'");
            }
        }

        @Override
        public boolean isDumbAware() {
            return Registry.is("dumb.aware.run.configurations");
        }
    }

    protected ProcessHandler myProcessHandler;
    protected GeneralCommandLine myGeneralCommandLine;

    protected StopProcessAction myStopProcessAction;

    private String displayName;// Friendly display name

    public RNConsoleImpl(@NotNull Project project, boolean viewer) {
        super(project, viewer);
    }

    public RNConsoleImpl(@NotNull Project project, @NotNull GlobalSearchScope searchScope, boolean viewer, boolean usePredefinedMessageFilter) {
        super(project, searchScope, viewer, usePredefinedMessageFilter);
    }

    protected RNConsoleImpl(@NotNull Project project, @NotNull GlobalSearchScope searchScope, boolean viewer, @NotNull ConsoleState initialState, boolean usePredefinedMessageFilter) {
        super(project, searchScope, viewer, initialState, usePredefinedMessageFilter);
    }

    public AnAction getReRunAction() {
        return new RerunAction();
    }

    public void setStopProcessAction(StopProcessAction myStopProcessAction) {
        this.myStopProcessAction = myStopProcessAction;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void reRun() {
        if(myGeneralCommandLine != null) {
            try {
                processCommandline(myGeneralCommandLine);
            } catch (ExecutionException e) {
                NotificationUtils.showNotification("Unable to run the commandline:" + e.getMessage(),
                        NotificationType.WARNING);
            }
        }
    }

    /**
     * 执行shell
     * 利用terminal换行即执行原理
     *
     * @param shell
     */
    public void executeShell(String shell, String workDirectory) {
        GeneralCommandLine commandLine =RNPathUtil.createFullPathCommandLine(shell, workDirectory);
        commandLine.setWorkDirectory(workDirectory);
        myGeneralCommandLine = commandLine;
        try {
            processCommandline(commandLine);
        } catch (ExecutionException e) {
            NotificationUtils.showNotification("Unable to run the commandline:" + e.getMessage(),
                    NotificationType.WARNING);
        }
    }

    /**
     * run gradle commands in android project dir
     * @param command
     */
    public void runGradleCI(String command) {
        String path = getProject().getBasePath();
        String gradleLocation = RNPathUtil.getAndroidProjectPath(path);
        if (gradleLocation == null) {
            NotificationUtils.gradleFileNotFound();
        } else {
            executeShell(command, gradleLocation);
        }
    }

    /**
     * run npm commands in package.json project dir
     * @param command
     */
    public void runNPMCI(String command) {
        String path = getProject().getBasePath();
        String npmLocation = RNPathUtil.getRNProjectPath(getProject(), path);

        if (npmLocation == null) {
            NotificationUtils.packageJsonNotFound();
        } else {
            executeShell(command, npmLocation);
        }
    }

    /* process command line, will very simple console view and tool window */
    private void processCommandline(GeneralCommandLine commandLine) throws ExecutionException {
        if(myProcessHandler != null) {
            ExecutionManagerImpl.stopProcess(myProcessHandler);
            myProcessHandler = null;
        }

        final OSProcessHandler processHandler = new OSProcessHandler(commandLine);
        myProcessHandler = processHandler;
        myStopProcessAction.setProcessHandler(processHandler);

        ProcessTerminatedListener.attach(processHandler);

        processConsole(processHandler);

//        ApplicationManager.getApplication().invokeLater(new Runnable() {
//            @Override
//            public void run() {
//                processConsole(processHandler);
//            }
//        });
    }

    /* process attach to console,show the log */
    // TODO: 2016/9/14 0014 need refactor console method
    private void processConsole(ProcessHandler processHandler) {
        clear();
        attachToProcess(processHandler);
        processHandler.startNotify();// Don't call this, the command content will not be shown
    }

    /**
     * Clean up process when close tab
     * @since 1.0.6
     */
    public void dispose() {
        super.dispose();
        if(myProcessHandler != null && !myProcessHandler.isProcessTerminated() ) {
            System.out.println("Terminate process of tab " + displayName + ", cmd:" + myGeneralCommandLine);
            ExecutionManagerImpl.stopProcess(myProcessHandler);
            myProcessHandler = null;
        }
    }

}