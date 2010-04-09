/*++
    Copyright  (c) 2004 SafeGen Software
    Contact information:
        mail: kab@safegen.com

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 
Module Name:
    essense.cpp

Abstract: Main plugin file

Revision History:

 kab        21/09/2004
      Initial release
			30/09/2004
	  Fill selection bounds to start/end address, if some range is selected...

--*/

#include "stdafx.h"
#include "save.h"
#include "load.h"
#include "StopWatch.h"

//--------------------------------------------------------------------------
//      Global vars
//--------------------------------------------------------------------------

TiXmlDocument xmlDoc;	// main xml doc
int iPluginFlags=0;		// flags

//--------------------------------------------------------------------------
//      Initialize.
//--------------------------------------------------------------------------
int idaapi init(void)
{
//  if ( inf.filetype != f_ELF ) return PLUGIN_SKIP;

// Please uncomment the following line to see how the notification works
//  hook_to_notification_point(HT_UI, sample_callback, NULL);

// Please uncomment the following line to see how the user-defined prefix works
//  set_user_defined_prefix(prefix_width, get_user_defined_prefix);

  return PLUGIN_OK;
}

//--------------------------------------------------------------------------
//      Terminate.
//--------------------------------------------------------------------------

void idaapi term(void)
{
}

//--------------------------------------------------------------------------
//      The plugin method
//--------------------------------------------------------------------------

void idaapi run(int arg)
{
	char init_dialog[] = 
		"STARTITEM 0\n"
		"Essense "
		ESSENSE_VERSION
		"\n"
		" Type:\n"		
		"<#Save function to XML#"
		"Save function to XML :R>"

		"<#Load function from XML#"
		"Load function from XML :R>>\n\n\n"

		"<~S~tarting address	:$:32:16::>\n"
		"<~E~nding address	:$:32:16::>\n"
		"<~F~ilename	:A:256:16::>\n"

		"<#Show debug info#"
		"Show debug information:C>\n"

		"<#Do not check CALL TO names#"
		"Ignore CALL TO names:C>\n"

		"<#Include autogenerated functions with default names (sub_xxxx)#"
		"Don't check names of functions:C>\n"

		"<#Store all detected by IDA functions#"
		"All functions:C>\n"

		"<#Do not save or restore any structures or enums#"
		"Don't save/restore structures:C>\n"

		"<#Load information anyway#"
		"Force load:C>>\n"
		;

	ea_t start_addr;
	ea_t end_addr;

	if (!read_selection(&start_addr, &end_addr))
	{
		func_t *f=get_func(get_screen_ea());
		if (f)
		{
			start_addr = f->startEA;
			end_addr = f->endEA;
		}
		else 
		{
			start_addr = get_screen_ea();
			end_addr = get_screen_ea();
		}
	}

	CStopWatch w;
	short sType=0;
	char buf[256];
	memset(buf, 0, 256);

	strcpy(buf, get_root_filename());
	strcat(buf, ".xml");

	int ok=AskUsingForm_c(init_dialog, &sType, &start_addr, &end_addr, buf, &iPluginFlags);

	if (ok)
	{
		w.Start();
		msg("Essense started\n");
		switch(sType)
		{
		case 0:
			{
				SaveFunction(start_addr, end_addr, buf);
				break;
			}
		case 1:
			{
				LoadFunction(start_addr, end_addr, buf);
				break;
			}
		}
		w.Stop();
		msg("Essense finished\n");
		msg("Execution takes: %02d:%02d.%d", w.GetTimeMinutes(), w.GetTimeSeconds(), w.GetTimeMSeconds());
	}
}

//--------------------------------------------------------------------------
char comment[] = "Essense IDA plugin.";

char help[] =
        "Essense plugin module\n"
        "\n"
        "This module helps you transfer data between different databases.\n"
        "\n";


//--------------------------------------------------------------------------
// This is the preferred name of the plugin module in the menu system
// The preferred name may be overriden in plugins.cfg file

char wanted_name[] = "Essense " \
						ESSENSE_VERSION;


// This is the preferred hotkey for the plugin module
// The preferred hotkey may be overriden in plugins.cfg file
// Note: IDA won't tell you if the hotkey is not correct
//       It will just disable the hotkey.

char wanted_hotkey[] = "Alt-1";


//--------------------------------------------------------------------------
//
//      PLUGIN DESCRIPTION BLOCK
//
//--------------------------------------------------------------------------
plugin_t PLUGIN =
{
  IDP_INTERFACE_VERSION,
  PLUGIN_UNL,           // plugin flags
  init,                 // initialize

  term,                 // terminate. this pointer may be NULL.

  run,                  // invoke plugin

  comment,              // long comment about the plugin
                        // it could appear in the status line
                        // or as a hint

  help,                 // multiline help about the plugin

  wanted_name,          // the preferred short name of the plugin
  wanted_hotkey         // the preferred hotkey to run the plugin
};