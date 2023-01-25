package green_green_avk.anotherterm;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.IntentCompat;
import androidx.core.math.MathUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownServiceException;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import green_green_avk.anotherterm.backends.BackendException;
import green_green_avk.anotherterm.backends.BackendModule;
import green_green_avk.anotherterm.backends.BackendUiInteraction;
import green_green_avk.anotherterm.backends.BackendsList;
import green_green_avk.anotherterm.backends.local.LocalModule;
import green_green_avk.anotherterm.backends.uart.UartModule;
import green_green_avk.anotherterm.ui.BackendUiSessionDialogs;
import green_green_avk.anotherterm.ui.BackendUiShell;
import green_green_avk.anotherterm.ui.UiUtils;
import green_green_avk.anotherterm.utils.BinaryGetOpts;
import green_green_avk.anotherterm.utils.BlockingSync;
import green_green_avk.anotherterm.utils.ChrootedFile;
import green_green_avk.anotherterm.utils.IntentUtils;
import green_green_avk.anotherterm.utils.LimitedInputStream;
import green_green_avk.anotherterm.utils.MimeType;
import green_green_avk.anotherterm.utils.Misc;
import green_green_avk.anotherterm.utils.PreferenceStorage;
import green_green_avk.anotherterm.utils.SslHelper;
import green_green_avk.anotherterm.utils.XmlToAnsi;
import green_green_avk.anothertermshellpluginutils.Plugin;
import green_green_avk.anothertermshellpluginutils.Protocol;
import green_green_avk.anothertermshellpluginutils.StringContent;
import green_green_avk.ptyprocess.PtyProcess;

public final class TermSh {
    private static final String NOTIFICATION_CHANNEL_ID = TermSh.class.getName();

    private static void checkFile(@NonNull final File file) throws FileNotFoundException {
        if (!file.exists())
            throw new FileNotFoundException("No such file");
        if (file.isDirectory())
            throw new FileNotFoundException("File is a directory");
    }

    private static final class UiBridge {
        @NonNull
        private final Context ctx;
        @NonNull
        private final Handler handler;

        private final AtomicInteger notificationId = new AtomicInteger(0);

        @Keep
        private final ConsoleService.Listener sessionsListener = new ConsoleService.Listener() {
            @Override
            protected void onSessionChange(final int key) {
                if (ConsoleService.isSessionTerminated(key))
                    removeUiNotification(key);
            }
        };

        {
            ConsoleService.addListener(sessionsListener);
        }

        @UiThread
        private UiBridge(@NonNull final Context context) {
            ctx = context;
            handler = new Handler(Looper.getMainLooper());
        }

        private void runOnUiThread(@NonNull final Runnable runnable) {
            handler.post(runnable);
        }

        private int getNextNotificationId() {
            return notificationId.getAndIncrement();
        }

        private void postUiNotification(final int key, @NonNull final String message) {
            handler.post(() -> {
                final Notification n = new NotificationCompat.Builder(
                        ctx.getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(message)
                        .setSmallIcon(R.drawable.ic_stat_serv)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(PendingIntent.getActivity(ctx, 0,
                                ConsoleActivity.getShowSessionIntent(ctx, key),
                                PendingIntent.FLAG_ONE_SHOT
                                        | PendingIntent.FLAG_UPDATE_CURRENT
                                        | PendingIntent.FLAG_IMMUTABLE))
                        .build();
                NotificationManagerCompat.from(ctx).notify(C.TERMSH_UI_TAG, key, n);
            });
        }

        private void removeUiNotification(final int key) {
            handler.post(() -> NotificationManagerCompat.from(ctx).cancel(C.TERMSH_UI_TAG, key));
        }

        private void postUiAwaitsNotification(final int key) {
            handler.post(() -> {
                final AnsiSession session;
                try {
                    session = ConsoleService.getAnsiSession(key);
                } catch (final NoSuchElementException e) {
                    return;
                }
                postUiNotification(key, "UI awaits in " + session.getTitle());
            });
        }

        private void waitForUiWithNotification(@NonNull final BackendUiSessionDialogs gui)
                throws InterruptedException {
            if (!gui.hasUi()) {
                final int key = gui.getSessionKey();
                postUiAwaitsNotification(key);
                try {
                    gui.waitForUi();
                } finally {
                    removeUiNotification(key);
                }
            }
        }

        private void postUserNotification(@NonNull final String message, final int id) {
            handler.post(() -> {
                final Notification n = new NotificationCompat.Builder(
                        ctx.getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(message)
                        .setSmallIcon(R.drawable.ic_stat_serv)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build();
                NotificationManagerCompat.from(ctx).notify(C.TERMSH_USER_TAG, id, n);
            });
        }

        private void removeUserNotification(final int id) {
            handler.post(() -> NotificationManagerCompat.from(ctx).cancel(C.TERMSH_USER_TAG, id));
        }
    }

    private static final class UiServer implements Runnable {
        private static final Pattern URI_PATTERN = Pattern.compile("^[a-zA-Z0-9+.-]+://");
        private static final BinaryGetOpts.Options URI_WEB_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("insecure", new String[]{"--insecure"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options CAT_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("insecure", new String[]{"--insecure"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("progress", new String[]{"--progress"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options COPY_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("force", new String[]{"-f", "--force"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("insecure", new String[]{"--insecure"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("progress", new String[]{"--progress"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("from-path", new String[]{"-fp", "--from-path"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("from-uri", new String[]{"-fu", "--from-uri"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("to-path", new String[]{"-tp", "--to-path"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("to-uri", new String[]{"-tu", "--to-uri"},
                                BinaryGetOpts.Option.Type.STRING)
                });
        private static final BinaryGetOpts.Options FAV_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("term", new String[]{"-t", "--term"},
                                BinaryGetOpts.Option.Type.STRING)
                });
        private static final BinaryGetOpts.Options NOTIFY_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("id", new String[]{"-i", "--id"},
                                BinaryGetOpts.Option.Type.INT),
                        new BinaryGetOpts.Option("remove", new String[]{"-r", "--remove"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options OPEN_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("notify", new String[]{"-N", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("prompt", new String[]{"-p", "--prompt"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("recipient", new String[]{"-r", "--recipient"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("uri", new String[]{"-u", "--uri"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options PICK_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("force", new String[]{"-f", "--force"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("insecure", new String[]{"--insecure"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("notify", new String[]{"-N", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("prompt", new String[]{"-p", "--prompt"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("uri", new String[]{"-u", "--uri"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options PLUGIN_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("help", new String[]{"-h", "--help"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options SEND_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("email-bcc", new String[]{"--email-bcc"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("email-cc", new String[]{"--email-cc"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("email-to", new String[]{"--email-to"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("html", new String[]{"--html"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("html-stdin", new String[]{"--html-stdin"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("name", new String[]{"-n", "--name"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("notify", new String[]{"-N", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("prompt", new String[]{"-p", "--prompt"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("size", new String[]{"-s", "--size"},
                                BinaryGetOpts.Option.Type.INT),
                        new BinaryGetOpts.Option("subject", new String[]{"--subject"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("text", new String[]{"--text"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("text-stdin", new String[]{"--text-stdin"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options SERIAL_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("adapter", new String[]{"-a", "--adapter"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("insecure", new String[]{"-i", "--insecure"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("list", new String[]{"-l", "--list"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options URI_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("close", new String[]{"-c", "--close-stream"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("list", new String[]{"-l", "--list-streams"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("name", new String[]{"-n", "--name"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("size", new String[]{"-s", "--size"},
                                BinaryGetOpts.Option.Type.INT),
                        new BinaryGetOpts.Option("wait", new String[]{"-w", "--wait"},
                                BinaryGetOpts.Option.Type.NONE)
                });

        private final UiBridge ui;

        private UiServer(@NonNull final UiBridge ui) {
            this.ui = ui;
        }

        private static final class ParseException extends RuntimeException {
            private ParseException(final String message) {
                super(message);
            }
        }

        private static final class ArgsException extends RuntimeException {
            private ArgsException(final String message) {
                super(message);
            }
        }

        private static final class ShellSecurityException extends RuntimeException {
            private static final String h = "The operation is not permitted in this session";

            private ShellSecurityException() {
                super(h);
            }

            private ShellSecurityException(final String message) {
                super(h + ": " + message);
            }
        }

        private static final class ShellUiException extends RuntimeException {
            private static final String h = "UI is inaccessible";

            private ShellUiException() {
                super(h);
            }

            private ShellUiException(final String message) {
                super(h + ": " + message);
            }
        }

        private static final class ShellCmdIO {
            private static final byte CMD_EXIT = 0;
            private static final byte CMD_OPEN = 1;
            private static final byte CMD_EXECP = 2;

            private static final int ARGLEN_MAX = 1024 * 1024;
            private static final byte[][] NOARGS = new byte[0][];

            private volatile boolean closed = false;
            private final Object closeLock = new Object();
            private final LocalSocket socket;
            private final InputStream cis;
            @NonNull
            private final InputStream stdIn;
            @NonNull
            private final OutputStream stdOut;
            @NonNull
            private final OutputStream stdErr;
            @NonNull
            private final InputStream ctlIn;
            private final long shellSessionToken;
            @Nullable
            private final LocalModule.SessionData shellSessionData;
            @NonNull
            private final byte[][] args;
            private volatile Runnable onTerminate = null;

            private final Thread cth = new Thread("TermShServer.Control") {
                @Override
                public void run() {
                    try {
                        while (true) {
                            final int r = ctlIn.read();
                            if (r < 0) break;
                        }
                    } catch (final IOException e) {
                        Log.e("TermShServer", "Request", e);
                    }
                    close();
                }
            };

            @Nullable
            private Runnable setOnTerminate(@Nullable final Runnable onTerminate) {
                synchronized (closeLock) {
                    if (onTerminate != null && closed) {
                        onTerminate.run();
                        return null;
                    }
                    final Runnable r = this.onTerminate;
                    this.onTerminate = onTerminate;
                    return r;
                }
            }

            private <T> T waitFor(@NonNull final Callable<T> task)
                    throws ExecutionException, InterruptedException {
                final Thread thread = Thread.currentThread();
                final Runnable ot = this.setOnTerminate(() -> {
                    try {
                        thread.interrupt();
                    } catch (final Throwable e) {
                        throw new Error("Cannot stop waiting! We are doomed!!!");
                    }
                });
                try {
                    return task.call();
                } catch (final Exception e) {
                    if (e instanceof InterruptedException)
                        throw (InterruptedException) e;
                    throw new ExecutionException(e);
                } finally {
                    this.setOnTerminate(ot);
                }
            }

            private <T> T waitFor(@NonNull final BlockingSync<T> result,
                                  @NonNull final Runnable onTerminate)
                    throws InterruptedException {
                final Runnable ot = this.setOnTerminate(onTerminate);
                try {
                    return result.get();
                } finally {
                    this.setOnTerminate(ot);
                }
            }

            private void close() {
                synchronized (closeLock) {
                    if (closed) return;
                    closed = true;
                    final Runnable ot = onTerminate;
                    if (ot != null) ot.run();
                    try {
                        if (stdIn != null) stdIn.close();
                    } catch (final IOException ignored) {
                    }
                    try {
                        if (stdOut != null) stdOut.close();
                    } catch (final IOException ignored) {
                    }
                    try {
                        if (stdErr != null) stdErr.close();
                    } catch (final IOException ignored) {
                    }
                }
            }

            @NonNull
            private static ParcelFileDescriptor wrapFD(@NonNull final FileDescriptor fd)
                    throws IOException {
                final ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(fd);
                try {
                    PtyProcess.close(fd);
                } catch (final IOException ignored) {
                }
                return pfd;
            }

            // TODO: better remove fallbacks

            @NonNull
            private static FileInputStream wrapInputFD(@NonNull final FileDescriptor fd) {
                try {
                    final ParcelFileDescriptor pfd = wrapFD(fd);
                    try {
                        return new PtyProcess.InterruptableFileInputStream(pfd);
                    } catch (final IOException e) {
                        Log.e("TermShServer", "Request", e);
                    }
                    return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                } catch (final IOException e) {
                    Log.e("TermShServer", "Request", e);
                }
                return new FileInputStream(fd);
            }

            @NonNull
            private static FileOutputStream wrapOutputFD(@NonNull final FileDescriptor fd) {
                try {
                    final ParcelFileDescriptor pfd = wrapFD(fd);
                    return new PtyProcess.PfdFileOutputStream(pfd);
                } catch (final IOException e) {
                    Log.e("TermShServer", "Request", e);
                }
                return new FileOutputStream(fd);
            }

            private static ParcelFileDescriptor wrapInputAsPipe(
                    @NonNull final PtyProcess.InterruptableFileInputStream is)
                    throws IOException {
                final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                final Thread thread = new Thread() {
                    @Override
                    public void run() {
                        final OutputStream os =
                                new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                        try {
                            Misc.copy(os, is);
                        } catch (final IOException ignored) {
                        } finally {
                            try {
                                os.close();
                            } catch (final IOException ignored) {
                            }
                        }
                    }
                };
                thread.setDaemon(true);
                thread.start();
                return pipe[0];
            }

            private static ParcelFileDescriptor wrapOutputAsPipe(
                    @NonNull final PtyProcess.PfdFileOutputStream os)
                    throws IOException {
                final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                final Thread thread = new Thread() {
                    @Override
                    public void run() {
                        final InputStream is =
                                new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
                        try {
                            Misc.copy(os, is);
                        } catch (final IOException ignored) {
                        } finally {
                            try {
                                is.close();
                            } catch (final IOException ignored) {
                            }
                        }
                    }
                };
                thread.setDaemon(true);
                thread.start();
                return pipe[1];
            }

            private static final class ExchangeableFds {
                @NonNull
                public final FileDescriptor[] fds;
                @Nullable
                private final Runnable onSent;

                public ExchangeableFds(@NonNull final FileDescriptor[] fds,
                                       @Nullable final Runnable onSent) {
                    this.fds = fds;
                    this.onSent = onSent;
                }

                public void recycle() {
                    if (onSent != null)
                        onSent.run();
                }
            }

            @NonNull
            private ExchangeableFds getExchangeableFds() throws IOException {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    return new ExchangeableFds(new FileDescriptor[]{
                            ((FileInputStream) stdIn).getFD(),
                            ((FileOutputStream) stdOut).getFD(),
                            ((FileOutputStream) stdErr).getFD()
                    }, null);
                }
                // ... avc: denied { read write } for
                // comm=4173796E635461736B202332 path="/dev/pts/0" dev="devpts" ino=3
                // scontext=u:r:untrusted_app:s0:c136,c256,c512,c768
                // tcontext=u:object_r:untrusted_app_all_devpts:s0:c135,c256,c512,c768
                // tclass=chr_file permissive=0
                // Google cares about our health!
                final Object[] streams = new Object[]{stdIn, stdOut, stdErr};
                final ParcelFileDescriptor[] toClose = new ParcelFileDescriptor[streams.length];
                final FileDescriptor[] wfds = new FileDescriptor[streams.length];
                for (int i = 0; i < streams.length; i++) {
                    if (streams[i] instanceof PtyProcess.InterruptableFileInputStream) {
                        final PtyProcess.InterruptableFileInputStream s =
                                (PtyProcess.InterruptableFileInputStream) streams[i];
                        if (PtyProcess.isatty(s.pfd)) {
                            toClose[i] = wrapInputAsPipe(s);
                            wfds[i] = toClose[i].getFileDescriptor();
                        } else {
                            toClose[i] = null;
                            wfds[i] = s.getFD();
                        }
                    } else if (streams[i] instanceof PtyProcess.PfdFileOutputStream) {
                        final PtyProcess.PfdFileOutputStream s =
                                (PtyProcess.PfdFileOutputStream) streams[i];
                        if (PtyProcess.isatty(s.pfd)) {
                            toClose[i] = wrapOutputAsPipe(s);
                            wfds[i] = toClose[i].getFileDescriptor();
                        } else {
                            toClose[i] = null;
                            wfds[i] = s.getFD();
                        }
                    } else {
                        throw new ClassCastException();
                    }
                }
                return new ExchangeableFds(wfds, () -> {
                    for (final ParcelFileDescriptor pfd : toClose) {
                        if (pfd != null) {
                            try {
                                pfd.close();
                            } catch (final IOException ignored) {
                            }
                        }
                    }
                });
            }

            private static long parseShellSessionToken(@NonNull final InputStream is)
                    throws IOException, ParseException {
                final DataInputStream dis = new DataInputStream(is);
                return dis.readLong();
            }

            @NonNull
            private static byte[][] parseArgs(@NonNull final InputStream is)
                    throws IOException, ParseException {
                /*
                 * It seems socket_read() contains a bug with exception throwing, see:
                 * https://android.googlesource.com/platform/frameworks/base/+/master/core/jni/android_net_LocalSocketImpl.cpp
                 * https://issuetracker.google.com/u/0/issues/156599236
                 */
                /*
                Possible W/A:
                final byte[] argc_b = new byte[1];
                final int r = is.read(argc_b);
                final int argc = r != 1 ? -1 : argc_b[0];
                */
                final int argc = is.read();
                if (argc <= 0) {
                    return NOARGS;
                }
                final byte[][] args = new byte[argc][];
                final DataInputStream dis = new DataInputStream(is);
                for (int i = 0; i < argc; ++i) {
                    final int l = dis.readInt();
                    if (l < 0 || l > ARGLEN_MAX) {
                        throw new ParseException("Arguments parse error");
                    }
                    args[i] = new byte[l];
                    dis.readFully(args[i]);
                }
                return args;
            }

            private static class ShellErrnoException extends IOException {
                public final int errno;

                public ShellErrnoException(final String message, final int errno) {
                    super(message);
                    this.errno = errno;
                }
            }

            private void exit(final int status) {
                try {
                    socket.getOutputStream().write(new byte[]{CMD_EXIT, (byte) status});
                } catch (final IOException ignored) {
                }
                close();
            }

            @NonNull
            private ParcelFileDescriptor open(@NonNull final String name, final int flags)
                    throws ParseException, IOException {
                final int errno;
                try {
                    final DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    dos.writeByte(CMD_OPEN);
                    dos.writeInt(flags);
                    final byte[] _name = Misc.toUTF8(name);
                    dos.writeInt(_name.length);
                    dos.write(_name);
                    final DataInputStream dis = new DataInputStream(socket.getInputStream());
                    final byte result = dis.readByte();
                    if (result == 0) {
                        final FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
                        if (fds.length != 1)
                            throw new ParseException("Invalid descriptors received");
                        return wrapFD(fds[0]);
                    }
                    errno = dis.readInt();
                } catch (final IOException e) {
                    throw new ParseException(e.getMessage());
                }
                switch (errno) {
                    case PtyProcess.ENOENT:
                        throw new FileNotFoundException(name + ": No such file or directory");
                    default:
                        throw new ShellErrnoException(name + ": open() fails with errno=" + errno, errno);
                }
            }

            // execvp(args[0], {args[1..], "/proc/<termsh-PID>/fd/<FD> ..."})
            private int execvp(@NonNull final String[] args, @NonNull final FileDescriptor[] fds)
                    throws ParseException, IOException {
                final int errno;
                try {
                    final DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    dos.writeByte(CMD_EXECP);
                    socket.setFileDescriptorsForSend(fds);
                    try {
                        dos.writeByte(args.length);
                    } finally {
                        socket.setFileDescriptorsForSend(null); // Non-null? Queue? HA-HA!
                    }
                    for (final String arg : args) {
                        final byte[] rawArg = Misc.toUTF8(arg);
                        dos.writeInt(rawArg.length);
                        dos.write(rawArg);
                    }
                    final DataInputStream dis = new DataInputStream(socket.getInputStream());
                    final byte result = dis.readByte();
                    if (result == 0) {
                        return dis.readInt();
                    }
                    errno = dis.readInt();
                } catch (final IOException e) {
                    throw new ParseException(e.getMessage());
                }
                switch (errno) {
                    case PtyProcess.ENOENT:
                        throw new FileNotFoundException(args[0] + ": No such file or directory");
                    default:
                        throw new ShellErrnoException(args[0] + ": execvp() fails with errno=" + errno, errno);
                }
            }

            private final ChrootedFile.Ops ops = new ChrootedFile.Ops() {
                @Override
                @NonNull
                public ParcelFileDescriptor open(@NonNull final String path, final int flags)
                        throws IOException, ParseException {
                    return ShellCmdIO.this.open(path, flags);
                }
            };

            @NonNull
            private ChrootedFile getOriginal(@NonNull final String name)
                    throws ParseException {
                return new ChrootedFile(ops, name);
            }

            @NonNull
            private File getOriginalFile(@NonNull final String name)
                    throws ParseException, IOException {
                return new File(PtyProcess.getPathByFd(open(name, PtyProcess.O_PATH).getFd()));
            }

            private void requireSessionState() {
                if (shellSessionData == null)
                    throw new ShellSecurityException("No session state");
            }

            private void requirePerms(final long perms) {
                if (shellSessionData == null ||
                        (shellSessionData.permissions &
                                perms) != perms) {
                    throw new ShellSecurityException();
                }
            }

            /**
             * @return attached GUI dialogs if any
             * @throws ShellUiException if no GUI is accessible at the moment
             */
            @NonNull
            private BackendUiSessionDialogs getGui() {
                if (shellSessionData == null)
                    throw new ShellUiException("No session state");
                final BackendUiInteraction ui = shellSessionData.ui;
                if (!(ui instanceof BackendUiSessionDialogs))
                    throw new ShellUiException("Not assigned");
                return (BackendUiSessionDialogs) ui;
            }

            @NonNull
            private BackendUiSessionDialogs waitForGuiWithNotification(@NonNull final UiBridge ui)
                    throws InterruptedException {
                final BackendUiSessionDialogs gui = getGui();
                try {
                    waitFor(() -> {
                        ui.waitForUiWithNotification(gui);
                        return null;
                    });
                } catch (final ExecutionException e) {
                    throw new ShellUiException(e.getMessage());
                }
                return gui;
            }

            private ShellCmdIO(@NonNull final LocalSocket socket)
                    throws IOException, ParseException {
                this.socket = socket;
                cis = socket.getInputStream();
                shellSessionToken = parseShellSessionToken(cis);
                args = parseArgs(cis);
                final FileDescriptor[] ioFds = socket.getAncillaryFileDescriptors();
                if (ioFds == null || ioFds.length != 4) {
                    if (ioFds != null)
                        for (final FileDescriptor fd : ioFds)
                            try {
                                PtyProcess.close(fd);
                            } catch (final IOException ignored) {
                            }
                    throw new ParseException("Bad descriptors");
                }
                stdIn = wrapInputFD(ioFds[0]);
                stdOut = wrapOutputFD(ioFds[1]);
                stdErr = wrapOutputFD(ioFds[2]);
                ctlIn = wrapInputFD(ioFds[3]);
                cth.start();

                // Post init
                try {
                    shellSessionData = LocalModule.getSessionData(shellSessionToken);
                } catch (final IllegalArgumentException e) {
                    final String msg = "SHELL_SESSION_TOKEN env var is wrong!";
                    stdErr.write(Misc.toUTF8(msg + "\n"));
                    exit(1);
                    throw new ShellSecurityException(msg);
                }
            }
        }

        @NonNull
        private static IOException fixURLConnectionException(@NonNull final IOException e,
                                                             @NonNull final Uri uri) {
            // fix for bad Android error reporting ;)
            if (e instanceof UnknownServiceException)
                return e;
            final String msg = e.getMessage();
            if (msg == null)
                return new IOException("Error getting content from " + uri);
            if (msg.substring(0, 4).equalsIgnoreCase("http"))
                return new IOException("Error getting content from " + e.getMessage());
            return e;
        }

        @NonNull
        private InputStream openInputStream(@NonNull final Uri uri, final boolean insecure)
                throws IOException {
            final InputStream is;
            final String scheme = uri.getScheme();
            if (scheme == null)
                throw new MalformedURLException("Malformed URL: " + uri);
            switch (scheme) {
                case "http":
                case "https": {
                    final URL url = new URL(uri.toString());
                    final URLConnection conn = url.openConnection();
                    if (conn instanceof HttpsURLConnection && insecure) {
                        ((HttpsURLConnection) conn)
                                .setSSLSocketFactory(SslHelper.trustAllCertsCtx.getSocketFactory());
                    }
                    try {
                        is = conn.getInputStream();
                    } catch (final IOException e) {
                        throw fixURLConnectionException(e, uri);
                    }
                    break;
                }
                default:
                    is = ui.ctx.getContentResolver().openInputStream(uri);
                    if (is == null) {
                        // Asset
                        throw new FileNotFoundException(uri + " does not exist");
                    }
            }
            return is;
        }

        @NonNull
        private OutputStream openOutputStream(@NonNull final Uri uri) throws FileNotFoundException {
            final OutputStream os = ui.ctx.getContentResolver().openOutputStream(uri);
            if (os == null) {
                // Asset
                throw new FileNotFoundException(uri + " does not exist");
            }
            return os;
        }

        @NonNull
        private ParcelFileDescriptor openFile(@NonNull final Uri uri) throws FileNotFoundException {
            final ContentResolver cr = ui.ctx.getContentResolver();
            ParcelFileDescriptor pfd;
            try {
                pfd = cr.openFileDescriptor(uri, "rw"); // Read only? We dunno, LOL...
                if (pfd == null)
                    throw new FileNotFoundException();
            } catch (final SecurityException | FileNotFoundException e) {
                pfd = cr.openFileDescriptor(uri, "r");
                if (pfd == null)
                    throw new FileNotFoundException();
            }
            return pfd;
        }

        @Nullable
        private static String deduceName(@NonNull final Uri uri) {
            return uri.getLastPathSegment();
        }

        @Nullable
        private String getName(@NonNull final Uri uri) {
            final Cursor c = ui.ctx.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c == null) return deduceName(uri);
            try {
                c.moveToFirst();
                final int ci = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ci < 0)
                    return deduceName(uri);
                return c.getString(ci);
            } catch (final Throwable e) {
                return deduceName(uri);
            } finally {
                c.close();
            }
        }

        private long getSize(@NonNull final Uri uri, final boolean insecure) throws IOException {
            final String scheme = uri.getScheme();
            if (scheme == null)
                throw new MalformedURLException("Malformed URL: " + uri);
            switch (scheme) {
                case "http":
                case "https": {
                    final URL url = new URL(uri.toString());
                    final URLConnection conn = url.openConnection();
                    if (conn instanceof HttpsURLConnection && insecure) {
                        ((HttpsURLConnection) conn)
                                .setSSLSocketFactory(SslHelper.trustAllCertsCtx.getSocketFactory());
                    }
                    try {
                        conn.connect();
                        return conn.getContentLength();
                    } catch (final IOException e) {
                        throw fixURLConnectionException(e, uri);
                    }
                }
            }
            final Cursor c = ui.ctx.getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE},
                    null, null, null);
            if (c == null)
                return -1;
            try {
                c.moveToFirst();
                final int ci = c.getColumnIndex(OpenableColumns.SIZE);
                if (ci < 0)
                    return -1;
                return c.getLong(ci);
            } catch (final Throwable e) {
                return -1;
            } finally {
                c.close();
            }
        }

        @Nullable
        private String getMime(@NonNull final Uri uri, final boolean insecure) throws IOException {
            final String scheme = uri.getScheme();
            if (scheme == null)
                throw new MalformedURLException("Malformed URL: " + uri);
            switch (scheme) {
                case "http":
                case "https": {
                    final URL url = new URL(uri.toString());
                    final URLConnection conn = url.openConnection();
                    if (conn instanceof HttpsURLConnection && insecure) {
                        ((HttpsURLConnection) conn)
                                .setSSLSocketFactory(SslHelper.trustAllCertsCtx.getSocketFactory());
                    }
                    try {
                        conn.connect();
                        return conn.getContentType();
                    } catch (final IOException e) {
                        throw fixURLConnectionException(e, uri);
                    }
                }
            }
            return ui.ctx.getContentResolver().getType(uri);
        }

        // limit is in chars
        @NonNull
        private static String readPipe(@NonNull final InputStream is, final int limit,
                                       @NonNull final String limitMsg)
                throws IOException {
            final char[] buf = new char[4096];
            final StringBuilder sb = new StringBuilder();
            final Reader isr = new InputStreamReader(is, Misc.UTF8);
            int len = 0;
            int n;
            while ((n = isr.read(buf)) > 0) {
                sb.append(buf, 0, n);
                len += n;
                if (len > limit)
                    throw new IOException(limitMsg);
            }
            return sb.toString();
        }

        private int getDefaultMarshallingLimit() {
            return ((App) ui.ctx.getApplicationContext()).settings.scratchpad_use_threshold * 1024;
        }

        private void printHelp(@NonNull final String value, @NonNull final OutputStream output)
                throws IOException {
            if (output instanceof PtyProcess.PfdFileOutputStream) {
                printHelp(value, output, ((PtyProcess.PfdFileOutputStream) output).pfd.getFd());
                return;
            }
            throw new IllegalArgumentException("Unsupported stream type");
        }

        private void printHelp(@NonNull final String value, @NonNull final OutputStream output,
                               @NonNull final ShellCmdIO shellCmd) throws IOException {
            // Any of the standard pipes can be redirected; using controlling terminal by itself
            final ParcelFileDescriptor pfd = shellCmd.open("/dev/tty", PtyProcess.O_RDWR);
            try {
                printHelp(value, output, pfd.getFd());
            } finally {
                pfd.close();
            }
        }

        private void printHelp(@NonNull final String value, @NonNull final OutputStream output,
                               final int ctfd) throws IOException {
            final int[] size = new int[4];
            PtyProcess.getSize(ctfd, size);
            try {
                final XmlToAnsi hp = new XmlToAnsi(value);
                hp.width = MathUtils.clamp(size[0], 20, 140);
                hp.indentStep = hp.width / 20;
                output.write(Misc.toUTF8("\n"));
                for (final String s : hp)
                    output.write(Misc.toUTF8(s));
                output.write(Misc.toUTF8("\n"));
            } catch (final Throwable e) {
                throw new IOException(ui.ctx.getString(
                        R.string.msg_xml_parse_error_s, e.getLocalizedMessage()));
            }
        }

        private static final class CopyProgressCallbacks implements Misc.CopyCallbacks {
            @NonNull
            final ShellCmdIO shellCmd;
            @NonNull
            final String suffix;

            private CopyProgressCallbacks(@NonNull final ShellCmdIO shellCmd,
                                          final long bytesTotal, @Nullable final String source) {
                this.shellCmd = shellCmd;
                suffix = (bytesTotal < 0 ? "" :
                        " / " + UiUtils.makeHumanReadableBytes(bytesTotal)) +
                        (source != null ? " of " + source : "") +
                        "\u001B[?7r";
            }

            @Override
            public void onProgress(final long bytesCopied) throws IOException {
                shellCmd.stdErr.write(Misc.toUTF8("\r\u001B[?7s\u001B[?7l" +
                        UiUtils.makeHumanReadableBytes(bytesCopied) + suffix));
            }

            @Override
            public void onFinish() throws IOException {
                shellCmd.stdErr.write(Misc.toUTF8("\r\u001B[2K"));
            }

            @Override
            public void onError() throws IOException {
                shellCmd.stdErr.write(Misc.toUTF8("\r\n"));
            }
        }

        private interface RunnableT {
            void run() throws Throwable;
        }

        private void runOnUiThread(@NonNull final ShellCmdIO shellCmd,
                                   @NonNull final RunnableT runnable)
                throws InterruptedException, IOException {
            final BlockingSync<Throwable> result = new BlockingSync<>();
            ui.runOnUiThread(() -> {
                try {
                    runnable.run();
                } catch (final Throwable e) {
                    result.set(e);
                    return;
                }
                result.set(null);
            });
            final Throwable e = shellCmd.waitFor(result, () ->
                    result.setIfIsNotSet(new IOException("Request terminated")));
            if (e != null) throw new IOException(e.getMessage());
        }

        @SuppressLint("StaticFieldLeak")
        private final class ClientTask extends AsyncTask<Object, Object, Object> {
            private volatile int exitStatus = 0;

            @Override
            @Nullable
            protected Object doInBackground(@NonNull final Object[] objects) {
                final LocalSocket socket = (LocalSocket) objects[0];
                final ShellCmdIO shellCmd;
                try {
                    if (Process.myUid() != socket.getPeerCredentials().getUid())
                        throw new ParseException("Spoofing detected!");
                    shellCmd = new ShellCmdIO(socket);
                } catch (final IOException | ParseException e) {
                    Log.e("TermShServer", "Request", e);
                    try {
                        socket.close();
                    } catch (final IOException ignored) {
                    }
                    return null;
                } catch (final ShellSecurityException e) {
                    Log.e("TermShServer", e.getMessage());
                    return null;
                }
                try {
                    exitStatus = 0;
                    if (shellCmd.args.length < 1) throw new ArgsException("No command specified");
                    final String command = Misc.fromUTF8(shellCmd.args[0]);
                    switch (command) {
                        case "help":
                            printHelp(ui.ctx.getString(R.string.desc_termsh_help),
                                    shellCmd.stdOut, shellCmd);
                            break;
                        case "notify": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(NOTIFY_OPTS);
                            final Integer _id = (Integer) opts.get("id");
                            if (opts.containsKey("remove")) {
                                if (_id == null)
                                    throw new ParseException("`id' argument is mandatory");
                                ui.removeUserNotification(_id);
                                break;
                            }
                            final int id = _id == null ? ui.getNextNotificationId() : _id;
                            final String msg;
                            switch (shellCmd.args.length - ap.position) {
                                case 1:
                                    msg = Misc.fromUTF8(shellCmd.args[ap.position]);
                                    break;
                                case 0: {
                                    final Reader reader =
                                            new InputStreamReader(shellCmd.stdIn, Misc.UTF8);
                                    final CharBuffer buf = CharBuffer.allocate(8192);
                                    String m = "";
                                    while (true) {
                                        ui.postUserNotification(m, id);
                                        if (reader.read(buf) < 0) break;
                                        if (buf.remaining() < 2) { // TODO: correct
                                            buf.position(buf.limit() / 2);
                                            buf.compact();
                                        }
                                        m = buf.duplicate().flip().toString();
                                    }
                                    msg = m;
                                    break;
                                }
                                default:
                                    throw new ParseException("Bad arguments");
                            }
                            ui.postUserNotification(msg, id);
                            break;
                        }
                        case "clipboard-copy": {
                            shellCmd.requirePerms(LocalModule.SessionData.PERM_CLIPBOARD_COPY);
                            if (!shellCmd.getGui().hasUi())
                                break;
                            final String value;
                            switch (shellCmd.args.length) {
                                case 1: {
                                    final int limit = 1024 * 1024;
                                    final LimitedInputStream limiter =
                                            new LimitedInputStream(shellCmd.stdIn, limit);
                                    final InputStreamReader reader =
                                            new InputStreamReader(limiter, Misc.UTF8);
                                    final StringBuilder sb = new StringBuilder();
                                    final char[] buf = new char[2048];
                                    int len;
                                    while ((len = reader.read(buf)) >= 0) {
                                        sb.append(buf, 0, len);
                                    }
                                    if (limiter.isLimitHit()) {
                                        shellCmd.getGui().showToast(ui.ctx.getString(
                                                R.string.msg_too_large_to_copy_to_clipboard));
                                        value = null;
                                    } else {
                                        value = sb.toString();
                                    }
                                    break;
                                }
                                case 2:
                                    value = Misc.fromUTF8(shellCmd.args[1]);
                                    break;
                                default:
                                    throw new ParseException("Bad arguments");
                            }
                            if (value != null && shellCmd.getGui().hasUi())
                                ui.runOnUiThread(() ->
                                        UiUtils.toClipboard(ui.ctx, value));
                            break;
                        }
                        case "uri": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(URI_OPTS);
                            if (opts.containsKey("list")) {
                                for (final Uri uri : StreamProvider.getBoundUriList()) {
                                    shellCmd.stdOut.write(Misc.toUTF8(uri.toString() + "\n"));
                                }
                            } else if (opts.containsKey("close"))
                                switch (shellCmd.args.length - ap.position) {
                                    case 1:
                                        StreamProvider.releaseUri(
                                                Uri.parse(Misc.fromUTF8(
                                                        shellCmd.args[ap.position])));
                                        break;
                                    default:
                                        throw new ParseException("No URI specified");
                                }
                            else
                                switch (shellCmd.args.length - ap.position) {
                                    case 0: {
                                        final BlockingSync<Object> result = new BlockingSync<>();
                                        String mime = (String) opts.get("mime");
                                        if (mime == null) mime = "*/*";
                                        final Uri uri = StreamProvider.obtainUri(shellCmd.stdIn,
                                                mime,
                                                (String) opts.get("name"),
                                                (Integer) opts.get("size"),
                                                msg -> result.set(null));
                                        shellCmd.stdOut.write(Misc.toUTF8(uri + "\n"));
                                        if (opts.containsKey("wait")) {
                                            shellCmd.waitFor(result, () -> {
                                                try {
                                                    StreamProvider.releaseUri(uri);
                                                } finally {
                                                    result.set(null);
                                                }
                                            });
                                        }
                                        break;
                                    }
                                    case 1: {
                                        final String filename =
                                                Misc.fromUTF8(shellCmd.args[ap.position]);
                                        final File file = shellCmd.getOriginalFile(filename);
                                        checkFile(file);
                                        final Uri uri = Misc.getFileUri(ui.ctx, file);
                                        shellCmd.stdOut.write(Misc.toUTF8(uri + "\n"));
                                        break;
                                    }
                                    default:
                                        throw new ParseException("Wrong number of arguments");
                                }
                            break;
                        }
                        case "view":
                        case "edit": {
                            final boolean writeable = "edit".equals(command);
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(OPEN_OPTS);
                            final String mime = (String) opts.get("mime");
                            String prompt = (String) opts.get("prompt");
                            if (prompt == null)
                                prompt = ui.ctx.getString(R.string.msg_pick_application);
                            if (shellCmd.args.length - ap.position == 1) {
                                final String filename =
                                        Misc.fromUTF8(shellCmd.args[ap.position]);
                                final Uri uri;
                                if (opts.containsKey("uri")) {
                                    uri = Uri.parse(filename);
                                } else {
                                    final File file = shellCmd.getOriginalFile(filename);
                                    checkFile(file);
                                    uri = Misc.getFileUri(ui.ctx, file);
                                }
                                final Intent i = new Intent(writeable ?
                                        Intent.ACTION_EDIT : Intent.ACTION_VIEW);
                                i.setDataAndType(uri, mime);
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | (writeable ?
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0));
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                                final Intent ci;
                                final String recipient = (String) opts.get("recipient");
                                if (recipient == null) {
                                    ci = Intent.createChooser(i, prompt);
                                    ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                                } else {
                                    if (recipient.indexOf('/') < 0)
                                        i.setClassName(ui.ctx.getApplicationContext(), recipient);
                                    else
                                        i.setComponent(ComponentName.unflattenFromString(recipient));
                                    ci = i;
                                }
                                if (opts.containsKey("notify"))
                                    RequesterActivity.showAsNotification(ui.ctx,
                                            ci,
                                            ui.ctx.getString(R.string.title_shell_of_s_script_notification_,
                                                    ui.ctx.getString(R.string.app_name)),
                                            prompt + " (" + filename + ")",
                                            NOTIFICATION_CHANNEL_ID,
                                            NotificationCompat.PRIORITY_HIGH);
                                else {
                                    shellCmd.waitForGuiWithNotification(ui);
                                    ui.ctx.startActivity(ci);
                                }
                            } else {
                                throw new ParseException("Bad arguments");
                            }
                            break;
                        }
                        case "send": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(SEND_OPTS);
                            final String mime = (String) opts.get("mime");
                            final String mimeType = mime != null ? mime : "*/*";
                            String prompt = (String) opts.get("prompt");
                            if (prompt == null)
                                prompt = ui.ctx.getString(R.string.msg_pick_destination);
                            final List<String> titles = new LinkedList<>();
                            final Set<Uri> uris = new HashSet<>();
                            final MimeType aggregateMime = new MimeType();
                            String name;
                            Uri uri;
                            final BlockingSync<Object> result = new BlockingSync<>();
                            final Runnable rCancel = () -> {
                                try {
                                    for (final Uri uri1 : uris)
                                        StreamProvider.releaseUri(uri1);
                                } finally {
                                    result.set(null);
                                }
                            };
                            boolean hasStdIn = false;
                            try {
                                for (int i = ap.position; i < shellCmd.args.length; i++) {
                                    name = Misc.fromUTF8(shellCmd.args[i]);
                                    if ("-".equals(name)) {
                                        if (hasStdIn)
                                            throw new ParseException("Error: more than one argument refers stdin");
                                        name = (String) opts.get("name");
                                        uri = StreamProvider.obtainUri(shellCmd.stdIn, mimeType,
                                                name, (Integer) opts.get("size"),
                                                msg -> result.set(null));
                                        titles.add(name != null ? name :
                                                ui.ctx.getString(R.string.name_stream_default));
                                        uris.add(uri);
                                        aggregateMime.quietMerge(mimeType);
                                        hasStdIn = true;
                                        continue;
                                    }
                                    if (URI_PATTERN.matcher(name).find()) {
                                        uri = Uri.parse(name);
                                        if (uris.contains(uri)) continue;
                                        final String title = getName(uri);
                                        titles.add(title != null ? title :
                                                ui.ctx.getString(R.string.name_stream_default));
                                    } else {
                                        final File file = shellCmd.getOriginalFile(name);
                                        checkFile(file);
                                        uri = Misc.getFileUri(ui.ctx, file);
                                        if (uris.contains(uri)) continue;
                                        titles.add(file.getName());
                                    }
                                    uris.add(uri);
                                    aggregateMime.quietMerge(ui.ctx.getContentResolver()
                                            .getType(uri));
                                }
                                if (!hasStdIn) result.set(null);
                                final Intent intent;
                                if (uris.size() > 1) {
                                    intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,
                                            new ArrayList<>(uris));
                                } else {
                                    intent = new Intent(Intent.ACTION_SEND);
                                    if (uris.size() == 1)
                                        intent.putExtra(Intent.EXTRA_STREAM, uris.iterator().next());
                                }
                                final boolean textStdin = opts.containsKey("text-stdin");
                                String text = (String) opts.get("text");
                                if (text != null) {
                                    if (textStdin)
                                        throw new ParseException("Error: --text and --text-stdin in the same time");
                                } else if (textStdin) {
                                    if (hasStdIn)
                                        throw new ParseException("Error: more than one argument refers stdin");
                                    hasStdIn = true;
                                }
                                final boolean htmlStdin = opts.containsKey("html-stdin");
                                String html = (String) opts.get("html");
                                if (html != null) {
                                    if (htmlStdin)
                                        throw new ParseException("Error: --html and --html-stdin in the same time");
                                } else if (htmlStdin) {
                                    if (hasStdIn)
                                        throw new ParseException("Error: more than one argument refers stdin");
                                    hasStdIn = true;
                                }
                                if (text == null && textStdin) {
                                    text = readPipe(shellCmd.stdIn, getDefaultMarshallingLimit(),
                                            "Text value exceeds marshalling limit");
                                } else if (html == null && htmlStdin) {
                                    html = readPipe(shellCmd.stdIn, getDefaultMarshallingLimit(),
                                            "HTML value exceeds marshalling limit");
                                }
                                if (text != null) {
                                    intent.putExtra(Intent.EXTRA_TEXT, (CharSequence) text);
                                }
                                if (html != null) {
                                    if (text == null) {
                                        intent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(html));
                                    }
                                    intent.putExtra(IntentCompat.EXTRA_HTML_TEXT, html);
                                    aggregateMime.quietMerge("text/html");
                                } else if (text != null) {
                                    aggregateMime.quietMerge("text/plain");
                                }
                                IntentUtils.putExtraIfSet(intent, Intent.EXTRA_SUBJECT,
                                        (String) opts.get("subject"));
                                IntentUtils.putSpaceListExtraIfSet(intent, Intent.EXTRA_EMAIL,
                                        (String) opts.get("email-to"));
                                IntentUtils.putSpaceListExtraIfSet(intent, Intent.EXTRA_CC,
                                        (String) opts.get("email-cc"));
                                IntentUtils.putSpaceListExtraIfSet(intent, Intent.EXTRA_BCC,
                                        (String) opts.get("email-bcc"));
                                intent.setType(aggregateMime.isSet ? aggregateMime.get() : mimeType);
                                if (opts.containsKey("notify"))
                                    RequesterActivity.showAsNotification(ui.ctx,
                                            Intent.createChooser(intent, prompt),
                                            ui.ctx.getString(R.string.title_shell_of_s_script_notification_,
                                                    ui.ctx.getString(R.string.app_name)),
                                            prompt +
                                                    " (" + TextUtils.join(", ", titles) + ")",
                                            NOTIFICATION_CHANNEL_ID,
                                            NotificationCompat.PRIORITY_HIGH);
                                else {
                                    shellCmd.waitForGuiWithNotification(ui);
                                    ui.ctx.startActivity(Intent.createChooser(intent, prompt)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                }
                            } catch (final Throwable e) {
                                rCancel.run();
                                throw e;
                            }
                            shellCmd.waitFor(result, rCancel);
                            break;
                        }
                        case "pick": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(PICK_OPTS);
                            String mime = (String) opts.get("mime");
                            if (mime == null) mime = "*/*";
                            String prompt = (String) opts.get("prompt");
                            if (prompt == null) prompt = ui.ctx.getString(R.string.msg_pick_source);

                            OutputStream output;
                            final ChrootedFile outputFile;
                            switch (shellCmd.args.length - ap.position) {
                                case 0:
                                    output = shellCmd.stdOut;
                                    outputFile = null;
                                    break;
                                case 1: {
                                    output = null;
                                    final String name = Misc.fromUTF8(shellCmd.args[ap.position]);
                                    final ChrootedFile f = shellCmd.getOriginal(name);
                                    if (f.isDirectory()) {
                                        outputFile = f;
                                    } else if (f.exists()) {
                                        if (!opts.containsKey("force")) {
                                            throw new ParseException("File already exists");
                                        }
                                        outputFile = f;
                                    } else {
                                        final ChrootedFile pf = f.getParent();
                                        if (pf == null || !pf.isDirectory()) {
                                            throw new ParseException("Directory does not exist");
                                        }
                                        outputFile = pf;
                                    }
                                    if (!outputFile.canWrite()) {
                                        throw new ParseException("Directory write access denied");
                                    }
                                    break;
                                }
                                default:
                                    throw new ParseException("Bad arguments");
                            }

                            final BlockingSync<Intent> r = new BlockingSync<>();
                            final Intent i = new Intent(Intent.ACTION_GET_CONTENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
                            final RequesterActivity.OnResult onResult = r::setIfIsNotSet;
                            final RequesterActivity.Request request;
                            if (opts.containsKey("notify"))
                                request = RequesterActivity.request(
                                        ui.ctx, Intent.createChooser(i, prompt), onResult,
                                        ui.ctx.getString(R.string.title_shell_of_s_script_notification_,
                                                ui.ctx.getString(R.string.app_name)),
                                        prompt, NOTIFICATION_CHANNEL_ID,
                                        NotificationCompat.PRIORITY_HIGH);
                            else {
                                try {
                                    shellCmd.waitForGuiWithNotification(ui);
                                    request = RequesterActivity.request(
                                            ui.ctx, Intent.createChooser(i, prompt), onResult);
                                } catch (final Throwable e) {
                                    r.setIfIsNotSet(null);
                                    throw e;
                                }
                            }
                            final Intent ri = shellCmd.waitFor(r, () -> {
                                try {
                                    request.cancel();
                                } finally {
                                    r.setIfIsNotSet(null);
                                }
                            });
                            final Uri uri;
                            if (ri == null || (uri = ri.getData()) == null) {
                                shellCmd.exit(1);
                                return null;
                            }
                            if (output == null) {
                                if (outputFile.isDirectory()) {
                                    String filename = getName(uri);
                                    if (filename == null) {
                                        shellCmd.stdErr.write(
                                                Misc.toUTF8("Cannot deduce file name\n"));
                                        filename = C.UNNAMED_FILE_NAME;
                                        exitStatus = 2;
                                    }
                                    final ChrootedFile of = outputFile.getChild(filename);
                                    if (!opts.containsKey("force") && of.isFile())
                                        throw new ParseException("File already exists");
                                    output = new FileOutputStream(of.create().getOriginalFile());
                                } else {
                                    output = new FileOutputStream(outputFile.getOriginalFile());
                                }
                            }
                            if (opts.containsKey("uri")) {
                                output.write(Misc.toUTF8(uri.toString()));
                            } else {
                                final InputStream is = openInputStream(uri,
                                        opts.containsKey("insecure"));
                                try {
                                    Misc.copy(output, is);
                                } finally {
                                    try {
                                        is.close();
                                    } finally {
                                        output.close();
                                    }
                                }
                            }
                            break;
                        }
                        case "copy": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(COPY_OPTS);
                            if (shellCmd.args.length - ap.position != 0)
                                throw new ParseException("Wrong number of arguments");
                            final InputStream is;
                            final OutputStream os;
                            String name;
                            Uri fromUri = null;
                            File fromFile = null;
                            boolean insecure = false;
                            if ((name = (String) opts.get("from-uri")) != null) {
                                fromUri = Uri.parse(name);
                                insecure = opts.containsKey("insecure");
                                is = openInputStream(fromUri, insecure);
                            } else if ((name = (String) opts.get("from-path")) != null) {
                                fromFile = shellCmd.getOriginalFile(name);
                                is = new FileInputStream(fromFile);
                            } else {
                                is = shellCmd.stdIn;
                            }
                            if ((name = (String) opts.get("to-uri")) != null) {
                                os = openOutputStream(Uri.parse(name));
                            } else if ((name = (String) opts.get("to-path")) != null) {
                                ChrootedFile of = shellCmd.getOriginal(name);
                                if (of.isDirectory()) {
                                    String filename = null;
                                    if (fromUri != null) {
                                        filename = getName(fromUri);
                                    } else if (fromFile != null) {
                                        filename = fromFile.getName();
                                    }
                                    if (filename == null) {
                                        shellCmd.stdErr.write(Misc.toUTF8("Cannot deduce file name\n"));
                                        filename = C.UNNAMED_FILE_NAME;
                                        exitStatus = 2;
                                    }
                                    of = of.getChild(filename);
                                }
                                if (!opts.containsKey("force") && of.isFile())
                                    throw new ParseException("File already exists");
                                os = new FileOutputStream(of.create().getOriginalFile());
                            } else {
                                os = shellCmd.stdOut;
                            }
                            try {
                                if (opts.containsKey("progress"))
                                    Misc.copy(shellCmd.stdOut, is,
                                            new CopyProgressCallbacks(shellCmd,
                                                    fromFile != null ? fromFile.length() :
                                                            fromUri != null ?
                                                                    getSize(fromUri, insecure) :
                                                                    -1, null),
                                            1000);
                                else
                                    Misc.copy(os, is);
                            } finally {
                                try {
                                    is.close();
                                } finally {
                                    os.close();
                                }
                            }
                            break;
                        }
                        case "cat": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(CAT_OPTS);
                            if (shellCmd.args.length - ap.position < 1) {
                                if (opts.containsKey("progress"))
                                    Misc.copy(shellCmd.stdOut, shellCmd.stdIn,
                                            new CopyProgressCallbacks(shellCmd, -1,
                                                    null),
                                            1000);
                                else
                                    Misc.copy(shellCmd.stdOut, shellCmd.stdIn);
                            } else for (int i = ap.position; i < shellCmd.args.length; ++i) {
                                final String argStr = Misc.fromUTF8(shellCmd.args[i]);
                                if ("-".equals(argStr)) {
                                    if (opts.containsKey("progress"))
                                        Misc.copy(shellCmd.stdOut, shellCmd.stdIn,
                                                new CopyProgressCallbacks(shellCmd, -1,
                                                        "<stdin>"),
                                                1000);
                                    else
                                        Misc.copy(shellCmd.stdOut, shellCmd.stdIn);
                                    continue;
                                }
                                final Uri uri = Uri.parse(argStr);
                                final boolean insecure = opts.containsKey("insecure");
                                final InputStream is = openInputStream(uri,
                                        insecure);
                                try {
                                    if (opts.containsKey("progress"))
                                        Misc.copy(shellCmd.stdOut, is,
                                                new CopyProgressCallbacks(shellCmd,
                                                        getSize(uri, insecure),
                                                        argStr),
                                                1000);
                                    else
                                        Misc.copy(shellCmd.stdOut, is);
                                } finally {
                                    is.close();
                                }
                            }
                            break;
                        }
                        case "with-uris": {
                            if (shellCmd.args.length < 4)
                                throw new ParseException("Not enough arguments");
                            final String[] args = new String[shellCmd.args.length - 2];
                            for (int i = 1; i < shellCmd.args.length - 1; i++)
                                args[i - 1] = Misc.fromUTF8(shellCmd.args[i]);
                            final String[] uris =
                                    Misc.fromUTF8(shellCmd.args[shellCmd.args.length - 1])
                                            .split("\\s+");
                            final ArrayList<ParcelFileDescriptor> pfds = new ArrayList<>();
                            try {
                                for (final String uriStr : uris) {
                                    pfds.add(openFile(Uri.parse(uriStr)));
                                }
                                final FileDescriptor[] fds = new FileDescriptor[pfds.size()];
                                for (int i = 0; i < pfds.size(); i++)
                                    fds[i] = pfds.get(i).getFileDescriptor();
                                exitStatus = shellCmd.execvp(args, fds);
                            } finally {
                                for (final ParcelFileDescriptor pfd : pfds)
                                    if (pfd != null) try {
                                        pfd.close();
                                    } catch (final IOException ignored) {
                                    }
                            }
                            break;
                        }
                        case "name": {
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final Uri uri = Uri.parse(Misc.fromUTF8(shellCmd.args[1]));
                            final String name = getName(uri);
                            if (name == null) {
                                shellCmd.stdOut.write(Misc.toUTF8(C.UNNAMED_FILE_NAME + "\n"));
                                exitStatus = 2;
                            } else
                                shellCmd.stdOut.write(Misc.toUTF8(name + "\n"));
                            break;
                        }
                        case "size": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(URI_WEB_OPTS);
                            if (shellCmd.args.length - ap.position != 1)
                                throw new ParseException("Wrong number of arguments");
                            final Uri uri = Uri.parse(Misc.fromUTF8(shellCmd.args[ap.position]));
                            final long size = getSize(uri, opts.containsKey("insecure"));
                            if (size < 0) {
                                shellCmd.stdOut.write(Misc.toUTF8(C.UNDEFINED_FILE_SIZE + "\n"));
                                exitStatus = 2;
                            } else
                                shellCmd.stdOut.write(Misc.toUTF8(size + "\n"));
                            break;
                        }
                        case "mime": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(URI_WEB_OPTS);
                            if (shellCmd.args.length - ap.position != 1)
                                throw new ParseException("Wrong number of arguments");
                            final Uri uri = Uri.parse(Misc.fromUTF8(shellCmd.args[ap.position]));
                            final String mime = getMime(uri, opts.containsKey("insecure"));
                            if (mime == null) {
                                shellCmd.stdOut.write(Misc.toUTF8(C.UNDEFINED_FILE_MIME + "\n"));
                                exitStatus = 2;
                            } else
                                shellCmd.stdOut.write(Misc.toUTF8(mime + "\n"));
                            break;
                        }
                        case "serial": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(SERIAL_OPTS);
                            if (opts.containsKey("list")) {
                                for (final Map.Entry<String, Integer> dev :
                                        UartModule.meta.getAdapters(ui.ctx).entrySet())
                                    shellCmd.stdOut.write(Misc.toUTF8(String.format(Locale.ROOT,
                                            "%s%s\n", dev.getKey(),
                                            dev.getValue() != BackendModule.Meta.ADAPTER_READY ?
                                                    " [Busy]" : "")));
                                break;
                            }
                            final Map<String, Object> params;
                            try {
                                params = shellCmd.args.length - ap.position > 0
                                        ? UartModule.meta.fromUri(Uri.parse(
                                        "uart:/" + Misc.fromUTF8(shellCmd.args[ap.position])))
                                        : new HashMap<>();
                            } catch (final BackendModule.ParametersUriParseException e) {
                                throw new ArgsException(e.getMessage());
                            }
                            params.put("insecure",
                                    opts.containsKey("insecure"));
                            final String adapter = (String) opts.get("adapter");
                            if (adapter != null)
                                params.put("adapter", adapter);
                            final BackendModule be = new UartModule();
                            be.setContext(ui.ctx);
                            be.setOnMessageListener(new BackendModule.OnMessageListener() {
                                @Override
                                public void onMessage(@NonNull final Object msg) {
                                    try {
                                        if (msg instanceof Throwable) {
                                            shellCmd.stdErr.write(Misc.toUTF8(((Throwable) msg)
                                                    .getMessage() + "\n"));
                                        } else if (msg instanceof String) {
                                            shellCmd.stdErr.write(Misc.toUTF8(msg + "\n"));
                                        } else if (msg instanceof BackendModule.StateMessage) {
                                            shellCmd.stdErr.write(Misc.toUTF8(
                                                    ((BackendModule.StateMessage) msg)
                                                            .message + "\n"));
                                        }
                                    } catch (final IOException ignored) {
                                    }
                                }
                            });
                            final BackendUiShell ui = new BackendUiShell();
                            ui.setIO(shellCmd.stdIn, shellCmd.stdOut, shellCmd.stdErr);
                            be.setUi(ui);
                            be.setOutputStream(shellCmd.stdOut);
                            final OutputStream toBe = be.getOutputStream();
                            try {
                                be.setParameters(params);
                                be.connect();
                                final byte[] buf = new byte[8192];
                                try {
                                    while (true) {
                                        final int r = shellCmd.stdIn.read(buf);
                                        if (r < 0) break;
                                        toBe.write(buf, 0, r);
                                    }
                                } catch (final IOException | BackendException e) {
                                    try {
                                        be.disconnect();
                                    } catch (final BackendException ignored) {
                                    }
                                    throw new IOException(e.getMessage());
                                }
                                be.disconnect();
                            } catch (final BackendException e) {
                                throw new IOException(e.getMessage());
                            } finally {
                                be.stop();
                            }
                            break;
                        }
                        case "uri-encode": {
                            switch (shellCmd.args.length) {
                                case 3: {
                                    final String allow = Misc.fromUTF8(shellCmd.args[2]);
                                    final String v = Misc.fromUTF8(shellCmd.args[1]);
                                    shellCmd.stdOut.write(Misc.toUTF8(
                                            Uri.encode(v, allow) + "\n"));
                                    break;
                                }
                                case 2: {
                                    final String v = Misc.fromUTF8(shellCmd.args[1]);
                                    shellCmd.stdOut.write(Misc.toUTF8(
                                            URLEncoder.encode(v, "UTF8") + "\n"));
                                    break;
                                }
                                default:
                                    throw new ParseException("Wrong number of arguments");
                            }
                            break;
                        }
                        case "uri-decode": {
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final String v = Misc.fromUTF8(shellCmd.args[1]);
                            final String r;
                            try {
                                r = URLDecoder.decode(v, "UTF8");
                            } catch (final IllegalArgumentException e) {
                                throw new ParseException(e.getMessage());
                            }
                            shellCmd.stdOut.write(Misc.toUTF8(r + "\n"));
                            break;
                        }
                        case "request-permission": {
                            shellCmd.requireSessionState();
                            if (shellCmd.args.length != 3)
                                throw new ParseException("Wrong number of arguments");
                            final String permStr = Misc.fromUTF8(shellCmd.args[1]);
                            final LocalModule.SessionData.PermMeta permMeta =
                                    LocalModule.SessionData.permByName.get(permStr);
                            if (permMeta == null) throw new ParseException("No such permission");
                            if ((shellCmd.shellSessionData.permissions & permMeta.bits)
                                    == permMeta.bits) {
                                exitStatus = 3;
                                break;
                            }
                            final String prompt = Misc.fromUTF8(shellCmd.args[2]);
                            final BackendUiSessionDialogs gui = shellCmd.waitForGuiWithNotification(ui);
                            shellCmd.setOnTerminate(() -> cancel(true));
                            try {
                                if (gui.promptYesNo(ui.ctx.getString(
                                        R.string.msg_permission_confirmation,
                                        ui.ctx.getString(
                                                permMeta.titleRes
                                        ), prompt)))
                                    synchronized (shellCmd.shellSessionData) {
                                        shellCmd.shellSessionData.permissions |= permMeta.bits;
                                    }
                                else exitStatus = 2;
                            } finally {
                                shellCmd.setOnTerminate(null);
                            }
                            break;
                        }
                        case "revoke-permission": {
                            shellCmd.requireSessionState();
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final String permStr = Misc.fromUTF8(shellCmd.args[1]);
                            final LocalModule.SessionData.PermMeta permMeta =
                                    LocalModule.SessionData.permByName.get(permStr);
                            if (permMeta == null) throw new ParseException("No such permission");
                            synchronized (shellCmd.shellSessionData) {
                                shellCmd.shellSessionData.permissions &= ~permMeta.bits;
                            }
                            break;
                        }
                        case "has-favorite": {
                            shellCmd.requirePerms(LocalModule.SessionData.PERM_FAVMGMT);
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final String name = Misc.fromUTF8(shellCmd.args[1]);
                            runOnUiThread(shellCmd, () -> {
                                if (!FavoritesManager.contains(name)) {
                                    exitStatus = 2;
                                }
                            });
                            break;
                        }
                        case "create-shell-favorite": {
                            shellCmd.requirePerms(LocalModule.SessionData.PERM_FAVMGMT);
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(FAV_OPTS);
                            if (shellCmd.args.length - ap.position != 2)
                                throw new ParseException("Wrong number of arguments");
                            final String name = Misc.fromUTF8(shellCmd.args[ap.position]);
                            final String execute = Misc.fromUTF8(shellCmd.args[ap.position + 1]);
                            runOnUiThread(shellCmd, () -> {
                                if (FavoritesManager.contains(name)) {
                                    exitStatus = 2;
                                    return;
                                }
                                final PreferenceStorage ps = new PreferenceStorage();
                                ps.put("type", BackendsList.get(LocalModule.class).typeStr);
                                ps.put("execute", execute);
                                if (opts.containsKey("term"))
                                    ps.put("terminal_string", opts.get("term"));
                                ps.put("wakelock.acquire_on_connect", true);
                                ps.put("wakelock.release_on_disconnect", true);
                                FavoritesManager.set(name, ps);
                            });
                            if (exitStatus == 2)
                                shellCmd.stdErr.write(Misc.toUTF8("Favorite `" + name
                                        + "' is already exists\n"));
                            break;
                        }
                        case "plugin": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(PLUGIN_OPTS);
                            if (shellCmd.args.length - ap.position < 1)
                                throw new ParseException("Wrong number of arguments");
                            final String pkgName = Misc.fromUTF8(shellCmd.args[ap.position]);
                            if (!PluginsManager.getBooleanFeature(pkgName,
                                    PluginsManager.F_ESSENTIAL))
                                shellCmd.requirePerms(LocalModule.SessionData.PERM_PLUGINEXEC);
                            final ComponentName cn = Plugin.getComponent(ui.ctx, pkgName);
                            if (cn == null)
                                throw new IOException(ui.ctx.getString(
                                        R.string.msg_s_is_not_a_plugin, pkgName));
                            if (!PluginsManager.verify(pkgName))
                                throw new IOException(ui.ctx.getString(
                                        R.string.msg_s_is_not_permitted_to_run, pkgName));
                            final Plugin plugin = Plugin.bind(ui.ctx, cn);
                            if (opts.containsKey("help")) {
                                try {
                                    final StringContent content = plugin.getMetaStringContent(
                                            Protocol.META_KEY_INFO_RES_ID,
                                            Protocol.META_KEY_INFO_RES_TYPE
                                    );
                                    if (content == null) {
                                        printHelp(ui.ctx.getString(R.string.msg_no_info_page),
                                                shellCmd.stdOut, shellCmd);
                                    } else if (content.type == Protocol.STRING_CONTENT_TYPE_XML_AT) {
                                        printHelp(content.text, shellCmd.stdOut, shellCmd);
                                    } else {
                                        shellCmd.stdOut.write(Misc.toUTF8(
                                                "\n" + content.text + "\n\n"));
                                    }
                                } finally {
                                    plugin.unbind();
                                }
                            } else {
                                final ShellCmdIO.ExchangeableFds efds =
                                        shellCmd.getExchangeableFds();
                                try {
                                    shellCmd.setOnTerminate(() ->
                                            plugin.signal(Protocol.SIG_FINALIZE));
                                    exitStatus = plugin.exec(
                                            Arrays.copyOfRange(shellCmd.args,
                                                    ap.position + 1, shellCmd.args.length),
                                            efds.fds
                                    );
                                } finally {
                                    shellCmd.setOnTerminate(null);
                                    efds.recycle();
                                    plugin.unbind();
                                }
                            }
                            break;
                        }
                        case "wakelock": {
                            shellCmd.requireSessionState();
                            if (shellCmd.args.length < 2)
                                throw new ParseException("Wrong number of arguments");
                            switch (Misc.fromUTF8(shellCmd.args[1])) {
                                case "is-held":
                                    exitStatus = shellCmd.shellSessionData.wakeLock.isHeld() ?
                                            0 : 2;
                                    break;
                                case "acquire":
                                    if (shellCmd.args.length > 2) {
                                        final long timeout;
                                        try {
                                            timeout = (long) (Float.parseFloat(
                                                    Misc.fromUTF8(shellCmd.args[2])) * 1000);
                                        } catch (final NumberFormatException e) {
                                            throw new ParseException(e.getMessage());
                                        }
                                        shellCmd.shellSessionData.wakeLock.acquire(timeout);
                                    } else {
                                        shellCmd.shellSessionData.wakeLock.acquire();
                                    }
                                    break;
                                case "release":
                                    shellCmd.shellSessionData.wakeLock.release();
                                    break;
                            }
                            break;
                        }
                        case "show-XWL-session":
                            shellCmd.requireSessionState();
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final long wlHelperUuid;
                            try {
                                wlHelperUuid = Long.decode(Misc.fromUTF8(shellCmd.args[1]));
                            } catch (final NumberFormatException e) {
                                throw new ParseException("Bad UUID: " + e.getMessage());
                            }
                            if (shellCmd.getGui().hasUi())
                                ui.runOnUiThread(() -> {
                                    try {
                                        final int sid = ((App) ui.ctx.getApplicationContext())
                                                .wlTermServer
                                                .getSessionKeyByHelperUuid(wlHelperUuid);
                                        final Intent intent = GraphicsConsoleActivity
                                                .getShowSessionIntent(ui.ctx, sid);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        ui.ctx.startActivity(intent);
                                    } catch (final RuntimeException ignored) {
                                    }
                                });
                            else
                                exitStatus = 2;
                            break;
                        default:
                            throw new ParseException("Unknown command");
                    }
                    shellCmd.exit(exitStatus);
                } catch (final InterruptedException | SecurityException | IOException |
                               ParseException | ArgsException | ShellSecurityException |
                               ShellUiException | BinaryGetOpts.ParseException |
                               ActivityNotFoundException e) {
                    try {
                        if (e instanceof ArgsException) {
                            printHelp(ui.ctx.getString(R.string.desc_termsh_help),
                                    shellCmd.stdErr, shellCmd);
                        }
                        shellCmd.stdErr.write(Misc.toUTF8(e.getMessage() + "\n"));
                        shellCmd.exit(1);
                    } catch (final IOException ignored) {
                    }
                }
                return null;
            }
        }

        @Override
        public void run() {
            LocalServerSocket serverSocket = null;
            try {
                serverSocket = new LocalServerSocket(BuildConfig.APPLICATION_ID + ".termsh");
                while (!Thread.interrupted()) {
                    final LocalSocket socket = serverSocket.accept();
                    ui.runOnUiThread(() -> new ClientTask()
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, socket));
                }
            } catch (final InterruptedIOException ignored) {
            } catch (final IOException e) {
                Log.e("TermShServer", "IO", e);
            } finally {
                if (serverSocket != null)
                    try {
                        serverSocket.close();
                    } catch (final IOException ignored) {
                    }
            }
        }
    }

    private final UiBridge ui;
    private final UiServer uiServer;
    private final Thread lth;

    @UiThread
    public TermSh(@NonNull final Context context) {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            final NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.title_shell_of_s,
                            context.getString(R.string.app_name)),
                    NotificationManager.IMPORTANCE_HIGH
            ));
        }

        ui = new UiBridge(context);
        uiServer = new UiServer(ui);
        lth = new Thread(uiServer, "TermShServer");
        lth.setDaemon(true);
        lth.start();
    }

    @Override
    protected void finalize() throws Throwable {
        lth.interrupt();
        super.finalize();
    }
}
