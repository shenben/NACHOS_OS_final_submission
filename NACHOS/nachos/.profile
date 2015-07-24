# ~/.profile: executed by Bourne-compatible login shells.

if [ "$BASH" ]; then
  if [ -f ~/.bashrc ]; then
    . ~/.bashrc
  fi
fi

mesg n

export NACHOSDIR="/home/lubuntu/Desktop/CSE150/cse150-su-project/NACHOS/nachos"
export ARCHDIR="$NACHOSDIR/mips-x86.linux-xgcc"
export PATH=$PATH:"$NACHOSDIR/bin"
export PATH=$PATH:"$ARCHDIR"
