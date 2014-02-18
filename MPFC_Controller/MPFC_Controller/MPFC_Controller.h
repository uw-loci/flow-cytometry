// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the MPFC_CONTROLLER_EXPORTS
// symbol defined on the command line. This symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// MPFC_CONTROLLER_API functions as being imported from a DLL, whereas this DLL sees symbols
// defined with this macro as being exported.
#ifdef MPFC_CONTROLLER_EXPORTS
#define MPFC_CONTROLLER_API __declspec(dllexport)
#else
#define MPFC_CONTROLLER_API __declspec(dllimport)
#endif
#ifdef __cplusplus
	extern "C" {
#endif

#include <stdint.h>
typedef int32_t int32;
#include "NIDAQmx.h"

// This class is exported from the MPFC_Controller.dll
class MPFC_CONTROLLER_API CMPFC_Controller {
public:
	CMPFC_Controller(void);
	// TODO: add your methods here.
	bool OnFlush(int intervalInMilliSec);
	bool OpenLine(int lineNum);
	bool CloseLine(int lineNum);
};

extern MPFC_CONTROLLER_API int openVal;
extern MPFC_CONTROLLER_API int closedVal;

//MPFC_CONTROLLER_API int fnMPFC_Controller(void);
MPFC_CONTROLLER_API bool OnFlush(int intervalInMilliSec);
MPFC_CONTROLLER_API bool OpenLine(int lineNum);
MPFC_CONTROLLER_API bool CloseLine(int lineNum);


#ifdef __cplusplus
	}
#endif