// MPFC_Controller.cpp : Defines the exported functions for the DLL application.
//

#include "stdafx.h"
#include "MPFC_Controller.h"
#include <stdio.h>
#include <string>

// This is an example of an exported variable
MPFC_CONTROLLER_API int openVal=1;
MPFC_CONTROLLER_API int closedVal=0;

// This is an example of an exported function.
//MPFC_CONTROLLER_API int fnMPFC_Controller(void)
//{
//	return 42;
//}

MPFC_CONTROLLER_API bool OnFlush(int intervalInMilliSec)
{
	uInt8 dataOPEN[1];
	uInt8 dataCLOSED[1];
	dataOPEN[0] = 1;
	dataCLOSED[0] = 0;

	TaskHandle line1Handle;
	TaskHandle line2Handle;
	TaskHandle line3Handle;
	TaskHandle line4Handle;

	line1Handle = 0;
	line2Handle = 0;
	line3Handle = 0;
	line4Handle = 0;

	int32 retval = 0;

	//line 1
	retval += DAQmxCreateTask("",&line1Handle);
	retval += DAQmxCreateDOChan(line1Handle,"Dev1/port0/line1","",DAQmx_Val_ChanForAllLines);

	//line 2
	retval += DAQmxCreateTask("",&line2Handle);
	retval += DAQmxCreateDOChan(line2Handle,"Dev1/port0/line2","",DAQmx_Val_ChanForAllLines);
	
	//line 3
	retval += DAQmxCreateTask("",&line3Handle);
	retval += DAQmxCreateDOChan(line3Handle,"Dev1/port0/line3","",DAQmx_Val_ChanForAllLines);

	//line 4
	retval += DAQmxCreateTask("",&line4Handle);
	retval += DAQmxCreateDOChan(line4Handle,"Dev1/port0/line4","",DAQmx_Val_ChanForAllLines);

	retval += DAQmxStartTask(line1Handle);
	retval += DAQmxStartTask(line2Handle);
	retval += DAQmxStartTask(line3Handle);
	retval += DAQmxStartTask(line4Handle);

	//make sure all data lines are set to 0 before beginning
	retval += DAQmxWriteDigitalLines(line1Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line2Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line3Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line4Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);

	//open rinse (line 4) and analysis (line 3) for 15 seconds
	retval += DAQmxWriteDigitalLines(line3Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataOPEN,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line4Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataOPEN,NULL,NULL);
	Sleep(intervalInMilliSec);

	//close again so pressure does not build up, especially if anything fails in between
	retval += DAQmxWriteDigitalLines(line4Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line3Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);

	//open rinse (line 4) and default/"junk" (line 1) for 15 seconds
	retval += DAQmxWriteDigitalLines(line1Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataOPEN,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line4Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataOPEN,NULL,NULL);
	Sleep(intervalInMilliSec);

	//close again so pressure does not build up, especially if anything fails in between
	retval += DAQmxWriteDigitalLines(line4Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line1Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);

	//open rinse (line 4) and desired (line 2) for 15 seconds
	retval += DAQmxWriteDigitalLines(line2Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataOPEN,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line4Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataOPEN,NULL,NULL);
	Sleep(intervalInMilliSec);

	//close again so pressure does not build up, especially if anything fails in between
	retval += DAQmxWriteDigitalLines(line4Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);
	retval += DAQmxWriteDigitalLines(line2Handle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);

	if(line1Handle != 0){
	retval += DAQmxStopTask(line1Handle);
	retval += DAQmxClearTask(line1Handle);
	}

	if(line2Handle != 0){
	retval += DAQmxStopTask(line2Handle);
	retval += DAQmxClearTask(line2Handle);
	}

	if(line3Handle != 0){
	retval += DAQmxStopTask(line3Handle);
	retval += DAQmxClearTask(line3Handle);
	}

	if(line4Handle != 0){
	retval += DAQmxStopTask(line4Handle);
	retval += DAQmxClearTask(line4Handle);
	}

	if(retval == 0) return true;
	return false;
}

std::string populatePortInfo(int deviceNum, int portNum, int lineNum)
{
	std::string portInfo = "Dev";
	portInfo.append(std::to_string(deviceNum));
	portInfo.append("/port");
	portInfo.append(std::to_string(portNum));
	portInfo.append("/line");
	portInfo.append(std::to_string(lineNum));
	return portInfo;
}

MPFC_CONTROLLER_API bool OpenLine(int deviceNum, int portNum, int lineNum)
{
	TaskHandle tmpHandle = 0;
	int32 retval = 0;

	std::string portInfo = populatePortInfo(deviceNum, portNum, lineNum);
	
	retval += DAQmxCreateTask("",&tmpHandle);
	retval += DAQmxCreateDOChan(tmpHandle,portInfo.c_str(),"",DAQmx_Val_ChanForAllLines);
	retval += DAQmxStartTask(tmpHandle);

	uInt8 dataOPEN[1];
	dataOPEN[0] = openVal;

	retval += DAQmxWriteDigitalLines(tmpHandle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataOPEN,NULL,NULL);

	if(tmpHandle != 0){
		retval += DAQmxStopTask(tmpHandle);
		retval += DAQmxClearTask(tmpHandle);
	}

	if(retval == 0) return true;
	return false;
}

MPFC_CONTROLLER_API bool CloseLine(int deviceNum, int portNum, int lineNum)
{
	TaskHandle tmpHandle = 0;
	int32 retval = 0;

	std::string portInfo = populatePortInfo(deviceNum, portNum, lineNum);
	
	retval += DAQmxCreateTask("",&tmpHandle);
	retval += DAQmxCreateDOChan(tmpHandle,portInfo.c_str(),"",DAQmx_Val_ChanForAllLines);
	retval += DAQmxStartTask(tmpHandle);

	uInt8 dataCLOSED[1];
	dataCLOSED[0] = closedVal;

	retval += DAQmxWriteDigitalLines(tmpHandle, 1, 1, 10.0, DAQmx_Val_GroupByChannel,dataCLOSED,NULL,NULL);

	if(tmpHandle != 0){
		retval += DAQmxStopTask(tmpHandle);
		retval += DAQmxClearTask(tmpHandle);
	}

	if(retval == 0) return true;
	return false;
}


// This is the constructor of a class that has been exported.
// see MPFC_Controller.h for the class definition
CMPFC_Controller::CMPFC_Controller()
{
	return;
}
