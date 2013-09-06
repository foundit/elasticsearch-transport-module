We usually use the "developers@found.no" pgp-key when publishing. sbt-pgp seems to
break if the GPG-keyring contains more than one key, or we try to sign with something
else than the default. If that had not been the case, the following should have worked
regardless of the number of keys stored in the local keyring:

    set usePgpKeyHex("440065AC58944314")

However, it currently does not. Until this suddenly starts working, make sure that
there is only one key in the keyring, or that the required key is the default chosen.