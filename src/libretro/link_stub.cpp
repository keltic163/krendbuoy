#include <cstdint>

extern "C" {

bool EmuReseted = false;
bool LinkFirstTime = false;
bool LinkIsWaiting = false;
uint8_t gbSIO_SC = 0;

}

int GetLinkMode()
{
    return 0;
}

void gbInitLink()
{
}

void gbLinkUpdate(uint8_t, int)
{
}

void StartLink(uint16_t)
{
}

void StartGPLink(uint16_t)
{
}

void LinkUpdate(int)
{
}

bool CheckLinkConnection()
{
    return false;
}
