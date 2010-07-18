#include <windows.h>
#include <stdio.h>
#include <tlhelp32.h>
#include <iostream>
#include <string>

void PrintUsage() {
	printf("Usage: runnerw.exe <app> <args>\n");
	printf("where <app> is console application and <args> it's arguments.\n");
	printf("\n");
	printf("Runner invokes console application as a process with inherited input and output streams.\n");
	printf("Input stream is scanned for presence of 2 char 255(IAC) and 243(BRK) sequence and generates Ctrl-Break event in that case.\n");
	printf("Also in case of all type of event(Ctrl-C, Close, Shutdown etc) Ctrl-Break event is generated.\n");

	exit(0);
}

void ErrorMessage(char *str) {

	LPVOID msg;

	FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
			NULL, GetLastError(), MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
			(LPTSTR) &msg, 0, NULL);

	printf("%s: %s\n", str, msg);
	LocalFree(msg);
}

void CtrlBreak() {
	if (!GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0)) {
		ErrorMessage("GenerateConsoleCtrlEvent");
	}
}

BOOL is_iac = FALSE;

char IAC = 'a';
char BRK = 'b';

BOOL Scan(char buf[], int count) {
	for (int i = 0; i<count; i++) {
		if (is_iac) {
			if (buf[i] == BRK) {
				CtrlBreak();
			} else {
				is_iac = FALSE;
			}
		}
		if (buf[i] == IAC) {
			is_iac = TRUE;
		}
	}

	return FALSE;
}

BOOL CtrlHandler( DWORD fdwCtrlType )
{
  switch( fdwCtrlType )
  {
    case CTRL_C_EVENT:
    case CTRL_CLOSE_EVENT:
    case CTRL_LOGOFF_EVENT:
    case CTRL_SHUTDOWN_EVENT:
      CtrlBreak();
      return( TRUE );
    case CTRL_BREAK_EVENT:
      return FALSE;
    default:
      return FALSE;
  }
}

int main(int argc, char * argv[]) {
	if (argc < 2) {
		PrintUsage();
	}

	std::string app(argv[1]);
	std::string args("");

	for (int i = 2; i < argc; i++) {
		args += " ";
		args += argv[i];
	}

	if (app.length() == 0) {
		PrintUsage();
	}

	STARTUPINFO si;
	SECURITY_ATTRIBUTES sa;
	PROCESS_INFORMATION pi;

	HANDLE newstdin, write_stdin;

	sa.lpSecurityDescriptor = NULL;

	sa.nLength = sizeof(SECURITY_ATTRIBUTES);
	sa.bInheritHandle = true;

	if (!CreatePipe(&newstdin, &write_stdin, &sa, 0)) {
		ErrorMessage("CreatePipe");
		exit(0);
	}

	GetStartupInfo(&si);

	si.dwFlags = STARTF_USESTDHANDLES ;
	si.wShowWindow = SW_HIDE;
	si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
	si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
	si.hStdInput = newstdin;

	char* c_app = new char[app.size() + 1];
	strcpy(c_app, app.c_str());

	char* c_args = new char[args.size() + 1];
	strcpy(c_args, args.c_str());

	SetConsoleCtrlHandler((PHANDLER_ROUTINE)CtrlHandler, TRUE);

	if (!CreateProcess(c_app,	 // Application name
						c_args,	// Application arguments
						NULL, NULL, TRUE, CREATE_DEFAULT_ERROR_MODE , NULL, NULL, &si, &pi)) {
		ErrorMessage("CreateProcess");
		CloseHandle(newstdin);
		CloseHandle(write_stdin);
		exit(0);
	}

	unsigned long exit = 0;
	unsigned long b_read;
	unsigned long b_write;
	unsigned long avail;

        char buf[1];
	memset(buf, 0, sizeof(buf));

	for (;;) {
		GetExitCodeProcess(pi.hProcess, &exit);

		if (exit != STILL_ACTIVE)
			break;

		 char c;
		 std::cin >> c;
//         char c = fgetc();
         buf[0] = c;
         Scan(buf, 1);
         WriteFile(write_stdin, buf, 1, &b_write, NULL);
	}

	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	CloseHandle(newstdin);
	CloseHandle(write_stdin);
}
