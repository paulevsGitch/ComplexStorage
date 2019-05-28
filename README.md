# ComplexStorage
A minecraft plugin with interface for big storage. Storage automatically sort and pack items inside.
Sources requires Spigot-API-1.14 or later.

## Config options

materials:
*  surface: GLASS - material for storage box faces;
*  filler: CHEST - material for storage box inside;
*  edge: SMOOTH_STONE - material for storage box frame;

size:
*  minimum: 4 - minimum size of storage box (by frame);
*  maximum: 20 - maximum size of storage box (by frame);

title:
*  back: §r§2Previous - title of left arrow in the interface;
*  next: §r§aNext - title of right arrow in the interface;
*  page-format: Page %d of %d - format string for the interface title. First %d is page number, second is total page count;

message:
*  in-usage: This storage is in use now - message for players, who tries to interact with storage, that is in use now.
